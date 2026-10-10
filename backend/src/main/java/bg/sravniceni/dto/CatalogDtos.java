package bg.sravniceni.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** DTO-та за публичния каталог. */
public final class CatalogDtos {

    private CatalogDtos() {
    }

    public record SettlementDto(String code, String name, long storeCount) {
    }

    public record ChainDto(Long id, String name, String color, long storeCount) {
    }

    public record CategoryDto(Short id, String name) {
    }

    public record CategoryGroupDto(Short id, String name, String icon, List<CategoryDto> categories) {
    }

    /**
     * Предложение за продукт във верига в избраното населено място.
     * price е най-ниската крайна цена (с промоция, ако има) сред магазините на веригата.
     */
    public record ProductOffer(
            Long productId,
            String name,
            Short categoryId,
            Long chainId,
            String chainName,
            String chainColor,
            BigDecimal price,
            BigDecimal regularPrice,
            boolean promo,
            int storeCount,
            BigDecimal unitPrice,
            String unit,
            LocalDate priceDate) {
    }

    public record StorePrice(Long storeId, String address, String settlementName,
                             BigDecimal price, BigDecimal promoPrice, LocalDate priceDate) {
    }

    public record HistoryPoint(LocalDate date, BigDecimal minPrice, BigDecimal avgPrice) {
    }

    public record ProductDetail(
            Long productId,
            String name,
            String code,
            Short categoryId,
            String categoryName,
            Long chainId,
            String chainName,
            String chainColor,
            BigDecimal packageQty,
            String packageUnit,
            List<StorePrice> stores,
            List<HistoryPoint> history,
            List<ProductOffer> alternatives) {
    }

    public record StatsDto(long chains, long stores, long products, long prices, LocalDate lastUpdate) {
    }

    public record PageResult<T>(List<T> items, int page, int size, boolean hasMore) {
    }
}
