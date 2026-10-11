package bg.sravniceni.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import bg.sravniceni.dto.BasketDtos.BasketItemRequest;

/** DTO-та за запазените списъци за пазаруване. */
public final class ListDtos {

    private ListDtos() {
    }

    public record ShoppingListRequest(
            @NotBlank(message = "Въведете име на списъка")
            @Size(max = 120, message = "Името е твърде дълго") String name,
            @NotNull @Size(max = 100, message = "Максимум 100 продукта в списък")
            List<@Valid BasketItemRequest> items) {
    }

    public record ListItemDto(Short categoryId, String keyword, int quantity, BigDecimal amount, String unit) {
    }

    public record ShoppingListDto(Long id, String name, Instant createdAt, Instant updatedAt, List<ListItemDto> items) {
    }

    public record MeDto(String id, String email, boolean admin) {
    }
}
