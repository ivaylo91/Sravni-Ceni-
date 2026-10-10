package bg.sravniceni.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import bg.sravniceni.domain.ShoppingList;
import bg.sravniceni.domain.ShoppingListItem;
import bg.sravniceni.dto.BasketDtos.BasketItemRequest;
import bg.sravniceni.dto.ListDtos.ListItemDto;
import bg.sravniceni.dto.ListDtos.ShoppingListDto;
import bg.sravniceni.dto.ListDtos.ShoppingListRequest;
import bg.sravniceni.repository.CategoryRepository;
import bg.sravniceni.repository.ShoppingListRepository;

/** Запазени списъци – всеки потребител вижда и променя само своите. */
@Service
@Transactional
public class ShoppingListService {

    private static final int MAX_LISTS_PER_USER = 50;

    private final ShoppingListRepository lists;
    private final CategoryRepository categories;

    public ShoppingListService(ShoppingListRepository lists, CategoryRepository categories) {
        this.lists = lists;
        this.categories = categories;
    }

    @Transactional(readOnly = true)
    public List<ShoppingListDto> findAll(UUID userId) {
        return lists.findByUserIdOrderByUpdatedAtDesc(userId).stream().map(ShoppingListService::toDto).toList();
    }

    @Transactional(readOnly = true)
    public ShoppingListDto get(UUID userId, long id) {
        return toDto(owned(userId, id));
    }

    public ShoppingListDto create(UUID userId, ShoppingListRequest request) {
        if (lists.countByUserId(userId) >= MAX_LISTS_PER_USER) {
            throw new IllegalArgumentException("Достигнахте максималния брой списъци (" + MAX_LISTS_PER_USER + ")");
        }
        var list = new ShoppingList(userId, request.name().trim());
        list.replaceItems(toItems(request.items()));
        return toDto(lists.save(list));
    }

    public ShoppingListDto update(UUID userId, long id, ShoppingListRequest request) {
        ShoppingList list = owned(userId, id);
        list.setName(request.name().trim());
        list.replaceItems(toItems(request.items()));
        return toDto(lists.saveAndFlush(list));
    }

    public void delete(UUID userId, long id) {
        lists.delete(owned(userId, id));
    }

    private ShoppingList owned(UUID userId, long id) {
        // Чужд списък връща 404, за да не издаваме, че съществува
        return lists.findByIdAndUserId(id, userId).orElseThrow(() -> new NotFoundException("Списъкът не е намерен"));
    }

    private List<ShoppingListItem> toItems(List<BasketItemRequest> items) {
        return items.stream().map(i -> {
            if (i.categoryId() != null && !categories.existsById(i.categoryId())) {
                throw new IllegalArgumentException("Непозната категория: " + i.categoryId());
            }
            String keyword = i.keyword() == null || i.keyword().isBlank() ? null : i.keyword().trim();
            return new ShoppingListItem(i.categoryId(), keyword, i.quantity());
        }).toList();
    }

    private static ShoppingListDto toDto(ShoppingList list) {
        return new ShoppingListDto(list.getId(), list.getName(), list.getCreatedAt(), list.getUpdatedAt(),
                list.getItems().stream()
                        .map(i -> new ListItemDto(i.getCategoryId(), i.getKeyword(), i.getQuantity()))
                        .toList());
    }
}
