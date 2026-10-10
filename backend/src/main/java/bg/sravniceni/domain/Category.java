package bg.sravniceni.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** Един от 101-те продукта в потребителската кошница на КЗП. */
@Entity
@Table(name = "category")
public class Category {

    @Id
    private Short id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id")
    private CategoryGroup group;

    @Column(nullable = false, length = 150)
    private String name;

    public Short getId() {
        return id;
    }

    public CategoryGroup getGroup() {
        return group;
    }

    public String getName() {
        return name;
    }
}
