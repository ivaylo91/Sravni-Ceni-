package bg.sravniceni.domain;

import java.math.BigDecimal;

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

    /** Желано количество в кг/л (по избор) – тогава quantity не се използва. */
    @Column(precision = 7, scale = 3)
    private BigDecimal amount;

    @Column(length = 3)
    private String unit;

    @Column(nullable = false)
    private int position;

    protected ShoppingListItem() {
    }

    public ShoppingListItem(Short categoryId, String keyword, int quantity, BigDecimal amount, String unit) {
        this.categoryId = categoryId;
        this.keyword = keyword;
        this.quantity = quantity;
        this.amount = amount;
        this.unit = unit;
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

    public BigDecimal getAmount() {
        return amount;
    }

    public String getUnit() {
        return unit;
    }

    public int getPosition() {
        return position;
    }
}
