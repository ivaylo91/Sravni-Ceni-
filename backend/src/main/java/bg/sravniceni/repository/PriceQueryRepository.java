package bg.sravniceni.repository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import bg.sravniceni.dto.CatalogDtos.ChainDto;
import bg.sravniceni.dto.CatalogDtos.HistoryPoint;
import bg.sravniceni.dto.CatalogDtos.ProductOffer;
import bg.sravniceni.dto.CatalogDtos.SettlementDto;
import bg.sravniceni.dto.CatalogDtos.StatsDto;
import bg.sravniceni.dto.CatalogDtos.StorePrice;

/**
 * Аналитични заявки върху цените. Таблиците price/price_history са големи
 * (стотици хиляди редове), затова тук се ползва директно SQL с агрегации,
 * а не JPA обекти.
 */
@Repository
public class PriceQueryRepository {

    /** Крайна цена: промоционалната, ако има такава. */
    public static final String EFFECTIVE_PRICE = "coalesce(pr.promo_price, pr.price)";

    private static final int MAX_SEARCH_WORDS = 6;

    private final NamedParameterJdbcTemplate jdbc;

    public PriceQueryRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ------------------------------------------------------------------ справочници

    public List<SettlementDto> settlementsWithStores() {
        return jdbc.query("""
                select st.code, st.name, count(s.id) as store_count
                from settlement st
                join store s on s.settlement_code = st.code
                group by st.code, st.name
                order by count(s.id) desc, st.name
                """, (rs, i) -> new SettlementDto(rs.getString("code"), rs.getString("name"), rs.getLong("store_count")));
    }

    public List<ChainDto> chains(String settlementCode) {
        var params = new MapSqlParameterSource();
        String filter = "";
        if (settlementCode != null) {
            filter = "and s.settlement_code = :settlement";
            params.addValue("settlement", settlementCode);
        }
        return jdbc.query("""
                select c.id, c.short_name, c.color, count(s.id) as store_count
                from chain c
                join store s on s.chain_id = c.id %s
                group by c.id, c.short_name, c.color
                order by count(s.id) desc, c.short_name
                """.formatted(filter), params,
                (rs, i) -> new ChainDto(rs.getLong("id"), rs.getString("short_name"), rs.getString("color"),
                        rs.getLong("store_count")));
    }

    public StatsDto stats() {
        return jdbc.getJdbcTemplate().queryForObject("""
                select (select count(*) from chain c where exists (select 1 from store s where s.chain_id = c.id)) as chains,
                       (select count(*) from store) as stores,
                       (select count(*) from product) as products,
                       (select count(*) from price) as prices,
                       (select max(price_date) from price) as last_update
                """, (rs, i) -> new StatsDto(rs.getLong("chains"), rs.getLong("stores"), rs.getLong("products"),
                rs.getLong("prices"), toLocalDate(rs.getDate("last_update"))));
    }

    // ------------------------------------------------------------------ търсене

    public enum SortOrder { PRICE, UNIT_PRICE, NAME }

    public record OfferQuery(String settlementCode, String text, Short categoryId, Long chainId,
                             SortOrder sort, int limit, int offset) {
    }

    /** Предложения (продукт × верига) с най-ниската цена в населеното място. */
    public List<ProductOffer> searchOffers(OfferQuery q) {
        var params = new MapSqlParameterSource();
        var where = new StringBuilder("where 1 = 1");
        if (q.settlementCode() != null) {
            where.append(" and s.settlement_code = :settlement");
            params.addValue("settlement", q.settlementCode());
        }
        if (q.categoryId() != null) {
            where.append(" and p.category_id = :category");
            params.addValue("category", q.categoryId());
        }
        if (q.chainId() != null) {
            where.append(" and p.chain_id = :chain");
            params.addValue("chain", q.chainId());
        }
        appendTextFilter(where, params, q.text());

        String order = switch (q.sort()) {
            case PRICE -> "price, p.name";
            case UNIT_PRICE -> "unit_price nulls last, price";
            case NAME -> "p.name, price";
        };
        params.addValue("limit", q.limit()).addValue("offset", q.offset());
        return jdbc.query("""
                select p.id, p.name, p.category_id, p.package_qty, p.package_unit,
                       c.id as chain_id, c.short_name, c.color,
                       min(%1$s) as price,
                       min(pr.price) as regular_price,
                       bool_or(pr.promo_price is not null) as promo,
                       count(*) as store_count,
                       max(pr.price_date) as price_date,
                       min(%1$s) / nullif(p.package_qty, 0) as unit_price
                from product p
                join chain c on c.id = p.chain_id
                join price pr on pr.product_id = p.id
                join store s on s.id = pr.store_id
                %2$s
                group by p.id, c.id
                order by %3$s
                limit :limit offset :offset
                """.formatted(EFFECTIVE_PRICE, where, order), params, OFFER_MAPPER);
    }

    /** Най-сходните продукти от същата категория в други вериги (по един на верига). */
    public List<ProductOffer> alternatives(long productId, String settlementCode, int limit) {
        var params = new MapSqlParameterSource("id", productId).addValue("limit", limit);
        String settlementFilter = "";
        if (settlementCode != null) {
            settlementFilter = "and s.settlement_code = :settlement";
            params.addValue("settlement", settlementCode);
        }
        return jdbc.query("""
                with src as (select name, category_id, chain_id from product where id = :id)
                select * from (
                    select distinct on (c.id)
                           p.id, p.name, p.category_id, p.package_qty, p.package_unit,
                           c.id as chain_id, c.short_name, c.color,
                           min(%1$s) as price,
                           min(pr.price) as regular_price,
                           bool_or(pr.promo_price is not null) as promo,
                           count(*) as store_count,
                           max(pr.price_date) as price_date,
                           min(%1$s) / nullif(p.package_qty, 0) as unit_price
                    from src
                    join product p on p.category_id = src.category_id and p.chain_id <> src.chain_id
                    join chain c on c.id = p.chain_id
                    join price pr on pr.product_id = p.id
                    join store s on s.id = pr.store_id %2$s
                    group by p.id, c.id, src.name
                    order by c.id, similarity(p.name, src.name) desc, min(%1$s)
                ) best
                order by price
                limit :limit
                """.formatted(EFFECTIVE_PRICE, settlementFilter), params, OFFER_MAPPER);
    }

    public List<StorePrice> storePrices(long productId, String settlementCode) {
        var params = new MapSqlParameterSource("id", productId);
        String filter = "";
        if (settlementCode != null) {
            filter = "and s.settlement_code = :settlement";
            params.addValue("settlement", settlementCode);
        }
        return jdbc.query("""
                select s.id, s.address, st.name as settlement_name, pr.price, pr.promo_price, pr.price_date
                from price pr
                join store s on s.id = pr.store_id
                join settlement st on st.code = s.settlement_code
                where pr.product_id = :id %s
                order by %s, s.address
                limit 200
                """.formatted(filter, EFFECTIVE_PRICE), params,
                (rs, i) -> new StorePrice(rs.getLong("id"), rs.getString("address"), rs.getString("settlement_name"),
                        rs.getBigDecimal("price"), rs.getBigDecimal("promo_price"), toLocalDate(rs.getDate("price_date"))));
    }

    public List<HistoryPoint> history(long productId, String settlementCode, int days) {
        var params = new MapSqlParameterSource("id", productId).addValue("days", days);
        String filter = "";
        if (settlementCode != null) {
            filter = "and settlement_code = :settlement";
            params.addValue("settlement", settlementCode);
        }
        return jdbc.query("""
                select price_date, min(min_price) as min_price, round(avg(avg_price), 2) as avg_price
                from price_history
                where product_id = :id %s
                  and price_date >= current_date - :days
                group by price_date
                order by price_date
                """.formatted(filter), params,
                (rs, i) -> new HistoryPoint(toLocalDate(rs.getDate("price_date")), rs.getBigDecimal("min_price"),
                        rs.getBigDecimal("avg_price")));
    }

    // ------------------------------------------------------------------ кошница

    public record StoreInfo(Long storeId, Long chainId, String chainName, String chainColor, String address) {
    }

    /** Най-евтиният продукт, отговарящ на ред от списъка, в даден магазин. */
    public record StoreMatch(Long storeId, Long productId, String productName, BigDecimal price, boolean promo) {
    }

    public List<StoreInfo> stores(String settlementCode, Collection<Long> chainIds) {
        var params = new MapSqlParameterSource("settlement", settlementCode);
        String chainFilter = "";
        if (chainIds != null && !chainIds.isEmpty()) {
            chainFilter = "and s.chain_id in (:chains)";
            params.addValue("chains", chainIds);
        }
        return jdbc.query("""
                select s.id, c.id as chain_id, c.short_name, c.color, s.address
                from store s join chain c on c.id = s.chain_id
                where s.settlement_code = :settlement %s
                """.formatted(chainFilter), params,
                (rs, i) -> new StoreInfo(rs.getLong("id"), rs.getLong("chain_id"), rs.getString("short_name"),
                        rs.getString("color"), rs.getString("address")));
    }

    /** За всеки магазин в населеното място – най-евтиният съвпадащ продукт. */
    public List<StoreMatch> cheapestMatchPerStore(String settlementCode, Short categoryId, String keyword,
                                                  Collection<Long> chainIds) {
        var params = new MapSqlParameterSource("settlement", settlementCode);
        var where = new StringBuilder("where s.settlement_code = :settlement");
        if (categoryId != null) {
            where.append(" and p.category_id = :category");
            params.addValue("category", categoryId);
        }
        if (chainIds != null && !chainIds.isEmpty()) {
            where.append(" and s.chain_id in (:chains)");
            params.addValue("chains", chainIds);
        }
        appendTextFilter(where, params, keyword);
        return jdbc.query("""
                select distinct on (pr.store_id)
                       pr.store_id, p.id as product_id, p.name, %1$s as price, pr.promo_price is not null as promo
                from price pr
                join product p on p.id = pr.product_id
                join store s on s.id = pr.store_id
                %2$s
                order by pr.store_id, %1$s, p.id
                """.formatted(EFFECTIVE_PRICE, where), params,
                (rs, i) -> new StoreMatch(rs.getLong("store_id"), rs.getLong("product_id"), rs.getString("name"),
                        rs.getBigDecimal("price"), rs.getBoolean("promo")));
    }

    // ------------------------------------------------------------------ помощни

    /** Всяка дума от търсенето трябва да се среща в името (без значение от реда и регистъра). */
    private static void appendTextFilter(StringBuilder where, MapSqlParameterSource params, String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        List<String> words = Arrays.stream(text.trim().split("\\s+"))
                .filter(w -> !w.isBlank())
                .limit(MAX_SEARCH_WORDS)
                .toList();
        for (int i = 0; i < words.size(); i++) {
            where.append(" and p.name ilike :w").append(i).append(" escape '\\'");
            params.addValue("w" + i, "%" + escapeLike(words.get(i)) + "%");
        }
    }

    static String escapeLike(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static LocalDate toLocalDate(Date date) {
        return date == null ? null : date.toLocalDate();
    }

    private static final RowMapper<ProductOffer> OFFER_MAPPER = PriceQueryRepository::mapOffer;

    private static ProductOffer mapOffer(ResultSet rs, int rowNum) throws SQLException {
        BigDecimal unitPrice = rs.getBigDecimal("unit_price");
        String unit = rs.getString("package_unit");
        return new ProductOffer(
                rs.getLong("id"),
                rs.getString("name"),
                rs.getShort("category_id"),
                rs.getLong("chain_id"),
                rs.getString("short_name"),
                rs.getString("color"),
                rs.getBigDecimal("price"),
                rs.getBigDecimal("regular_price"),
                rs.getBoolean("promo"),
                rs.getInt("store_count"),
                unitPrice == null || "pcs".equals(unit) ? null : unitPrice.setScale(2, RoundingMode.HALF_UP),
                unitPrice == null || "pcs".equals(unit) ? null : unit,
                toLocalDate(rs.getDate("price_date")));
    }
}
