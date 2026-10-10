package bg.sravniceni.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Ред от списък: категория от кошницата (напр. „Прясно мляко“) и/или ключова
 * дума (напр. „Верея 3%“). Така един ред съвпада с продукти от всички вериги.
 */
@Entity
@Table(name = "shopping_list_item")
public class ShoppingListItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "list_id")
    private ShoppingList list;

    @Column(name = "category_id")
    private Short categoryId;

    @Column(length = 100)
    private String keyword;

    @Column(nullable = false)
    private int quantity;

    @Column(nullable = false)
    private int position;

    protected ShoppingListItem() {
    }

    public ShoppingListItem(Short categoryId, String keyword, int quantity) {
        this.categoryId = categoryId;
        this.keyword = keyword;
        this.quantity = quantity;
    }

    void attach(ShoppingList list, int position) {
        this.list = list;
        this.position = position;
    }

    public Long getId() {
        return id;
    }

    public Short getCategoryId() {
        return categoryId;
    }

    public String getKeyword() {
        return keyword;
    }

    public int getQuantity() {
        return quantity;
    }

    public int getPosition() {
        return position;
    }
}
