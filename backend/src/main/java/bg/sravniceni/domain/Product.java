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

/** Продукт на конкретна верига. Кодът е уникален само в рамките на веригата. */
@Entity
@Table(name = "product")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "chain_id")
    private Chain chain;

    @Column(nullable = false, length = 64)
    private String code;

    @Column(nullable = false, length = 500)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id")
    private Category category;

    @Column(name = "package_qty", precision = 10, scale = 3)
    private BigDecimal packageQty;

    @Column(name = "package_unit", length = 3)
    private String packageUnit;

    public Long getId() {
        return id;
    }

    public Chain getChain() {
        return chain;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public Category getCategory() {
        return category;
    }

    public BigDecimal getPackageQty() {
        return packageQty;
    }

    public String getPackageUnit() {
        return packageUnit;
    }
}
