package bg.sravniceni.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** DTO-та за изчисляване на най-евтина кошница. */
public final class BasketDtos {

    private BasketDtos() {
    }

    /** Ред от списъка: категория и/или ключова дума, количество. */
    public record BasketItemRequest(
            Short categoryId,
            @Size(max = 100, message = "Ключовата дума е твърде дълга") String keyword,
            @Min(value = 1, message = "Количеството трябва да е поне 1")
            @Max(value = 99, message = "Количеството трябва да е най-много 99") int quantity) {

        @AssertTrue(message = "Изберете категория или въведете ключова дума")
        public boolean isCriteriaPresent() {
            return categoryId != null || (keyword != null && !keyword.isBlank());
        }
    }

    public record BasketRequest(
            @NotBlank(message = "Изберете населено място")
            @Pattern(regexp = "\\d{5}", message = "Невалиден код на населено място") String settlementCode,
            @NotEmpty(message = "Списъкът е празен")
            @Size(max = 50, message = "Максимум 50 продукта в кошницата") List<@Valid BasketItemRequest> items,
            Set<Long> chainIds) {
    }

    /** Избраният (най-евтин) продукт за даден ред от списъка. */
    public record BasketLine(
            int itemIndex,
            Long productId,
            String productName,
            BigDecimal price,
            boolean promo,
            int quantity,
            BigDecimal lineTotal,
            Long storeId,
            String chainName,
            String address) {
    }

    /** Цялата кошница, купена от един магазин. */
    public record StoreBasket(
            Long storeId,
            Long chainId,
            String chainName,
            String chainColor,
            String address,
            BigDecimal total,
            int foundCount,
            List<Integer> missingItems,
            List<BasketLine> lines) {
    }

    /** Най-ниската възможна сума, ако пазарувате от няколко магазина. */
    public record SplitBasket(BigDecimal total, int storeCount, List<Integer> missingItems, List<BasketLine> lines) {
    }

    public record BasketResult(
            String settlementCode,
            int itemCount,
            List<StoreBasket> bestStores,
            List<StoreBasket> bestPerChain,
            SplitBasket split,
            BigDecimal savings) {
    }
}
