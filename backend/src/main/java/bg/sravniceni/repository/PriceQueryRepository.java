package bg.sravniceni.repository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

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
    private final ObjectMapper json;

    public PriceQueryRepository(NamedParameterJdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
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
                       min(%1$s) / nullif(coalesce(p.package_qty, 1), 0) as unit_price
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
                           min(%1$s) / nullif(coalesce(p.package_qty, 1), 0) as unit_price
                    from src
                    join product p on p.category_id = src.category_id and p.chain_id <> src.chain_id
                    join chain c on c.id = p.chain_id
                    join price pr on pr.product_id = p.id
                    join store s on s.id = pr.store_id %2$s
                    group by p.id, c.id, src.name
                    order by c.id, extensions.similarity(p.name, src.name) desc, min(%1$s)
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

    /** Ред от кошницата за заявката. amount/unit са по избор (кг или л). */
    public record BasketQueryItem(int index, Short categoryId, String keyword, int quantity,
                                  BigDecimal amount, String unit) {
    }

    /** Най-евтиният вариант за ред от кошницата в даден магазин. packs е броят опаковки. */
    public record StoreMatch(int itemIndex, Long storeId, Long productId, String productName, BigDecimal price,
                             boolean promo, int packs, BigDecimal lineTotal, BigDecimal packageQty, String packageUnit) {
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

    /**
     * За всеки ред от кошницата и всеки магазин в населеното място – най-евтиният
     * съвпадащ продукт. Всички редове се изчисляват с една заявка (LATERAL), за да
     * има само едно отиване до базата.
     * <p>
     * Без amount: сума = брой × цена на опаковка. С amount (кг/л): само продукти
     * с разпозната разфасовка в същата мярка; купуват се ceil(amount / разфасовка)
     * опаковки и се избира най-ниската обща сума. Стоката на тегло се плаща точно
     * (amount × цена за кг).
     */
    public List<StoreMatch> cheapestMatches(String settlementCode, List<BasketQueryItem> items, Collection<Long> chainIds) {
        var params = new MapSqlParameterSource("settlement", settlementCode).addValue("items", toJson(items));
        String chainFilter = "";
        if (chainIds != null && !chainIds.isEmpty()) {
            chainFilter = "and s.chain_id in (:chains)";
            params.addValue("chains", chainIds);
        }
        return jdbc.query("""
                with items as (
                    select * from jsonb_to_recordset(cast(:items as jsonb))
                        as i(idx int, category smallint, words text[], quantity int, amount numeric, unit text)
                )
                select i.idx, m.*
                from items i
                cross join lateral (
                    select distinct on (pr.store_id)
                           pr.store_id, p.id as product_id, p.name, p.package_qty, p.package_unit,
                           c.eff as price, pr.promo_price is not null as promo, c.packs, t.line_total
                    from price pr
                    join product p on p.id = pr.product_id
                    join store s on s.id = pr.store_id
                    cross join lateral (
                        select coalesce(pr.promo_price, pr.price) as eff,
                               case when i.amount is null or p.package_qty is null then i.quantity
                                    else ceil(i.amount / nullif(p.package_qty, 0))::int end as packs
                    ) c
                    cross join lateral (
                        -- стока на тегло (package_qty е null): плаща се точното количество × цена за кг
                        select case when i.amount is not null and p.package_qty is null
                                    then round(i.amount * c.eff, 2)
                                    else c.packs * c.eff end as line_total
                    ) t
                    where s.settlement_code = :settlement %s
                      and (i.category is null or p.category_id = i.category)
                      and p.name ilike all (i.words)
                      and (i.amount is null or (p.package_unit = i.unit and (p.package_qty is null or p.package_qty > 0)))
                    order by pr.store_id, t.line_total, c.eff, p.id
                ) m
                """.formatted(chainFilter), params,
                (rs, i) -> new StoreMatch(rs.getInt("idx"), rs.getLong("store_id"), rs.getLong("product_id"),
                        rs.getString("name"), rs.getBigDecimal("price"), rs.getBoolean("promo"), rs.getInt("packs"),
                        rs.getBigDecimal("line_total"), rs.getBigDecimal("package_qty"), rs.getString("package_unit")));
    }

    /** Редовете като JSON за jsonb_to_recordset; думите са готови ILIKE шаблони (escape символът по подразбиране е обратна наклонена черта). */
    private String toJson(List<BasketQueryItem> items) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (BasketQueryItem item : items) {
            Map<String, Object> row = new HashMap<>();
            row.put("idx", item.index());
            row.put("category", item.categoryId());
            row.put("quantity", item.quantity());
            row.put("amount", item.amount());
            row.put("unit", item.unit());
            row.put("words", words(item.keyword()).stream().map(w -> "%" + escapeLike(w) + "%").toList());
            rows.add(row);
        }
        try {
            return json.writeValueAsString(rows);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    // ------------------------------------------------------------------ помощни

    /** Всяка дума от търсенето трябва да се среща в името (без значение от реда и регистъра). */
    private static void appendTextFilter(StringBuilder where, MapSqlParameterSource params, String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        List<String> words = words(text);
        for (int i = 0; i < words.size(); i++) {
            where.append(" and p.name ilike :w").append(i).append(" escape '\\'");
            params.addValue("w" + i, "%" + escapeLike(words.get(i)) + "%");
        }
    }

    private static List<String> words(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        return Arrays.stream(text.trim().split("\\s+"))
                .filter(w -> !w.isBlank())
                .limit(MAX_SEARCH_WORDS)
                .toList();
    }

    static String escapeLike(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static LocalDate toLocalDate(Date date) {
        return date == null ? null : date.toLocalDate();
    }

    private static final RowMapper<ProductOffer> OFFER_MAPPER = PriceQueryRepository::mapOffer;

    private static ProductOffer mapOffer(ResultSet rs, int rowNum) throws SQLException {
        String unit = rs.getString("package_unit");
        BigDecimal unitPrice = unit == null ? null : rs.getBigDecimal("unit_price");
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
