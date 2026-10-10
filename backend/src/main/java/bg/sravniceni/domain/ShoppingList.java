package bg.sravniceni.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

/** Списък за пазаруване на потребител (userId = id от Supabase Auth). */
@Entity
@Table(name = "shopping_list")
public class ShoppingList {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "list", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position")
    private List<ShoppingListItem> items = new ArrayList<>();

    protected ShoppingList() {
    }

    public ShoppingList(UUID userId, String name) {
        this.userId = userId;
        this.name = name;
    }

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
        if (createdAt == null) {
            createdAt = updatedAt;
        }
    }

    public void replaceItems(List<ShoppingListItem> newItems) {
        items.clear();
        for (int i = 0; i < newItems.size(); i++) {
            ShoppingListItem item = newItems.get(i);
            item.attach(this, i);
            items.add(item);
        }
        updatedAt = Instant.now(); // промяна само в елементите също обновява списъка
    }

    public Long getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public List<ShoppingListItem> getItems() {
        return items;
    }
}
