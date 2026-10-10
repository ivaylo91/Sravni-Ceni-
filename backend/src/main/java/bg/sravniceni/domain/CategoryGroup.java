package bg.sravniceni.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "category_group")
public class CategoryGroup {

    @Id
    private Short id;

    @Column(nullable = false, length = 100)
    private String name;

    /** Име на иконата от FontAwesome (без префикс fa-). */
    @Column(nullable = false, length = 50)
    private String icon;

    @Column(name = "sort_order", nullable = false)
    private short sortOrder;

    public Short getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getIcon() {
        return icon;
    }

    public short getSortOrder() {
        return sortOrder;
    }
}
