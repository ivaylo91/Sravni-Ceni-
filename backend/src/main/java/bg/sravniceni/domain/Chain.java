package bg.sravniceni.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Търговска верига (Kaufland, Lidl, Billa…). */
@Entity
@Table(name = "chain")
public class Chain {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 20)
    private String eik;

    @Column(nullable = false)
    private String name;

    @Column(name = "short_name", nullable = false, length = 120)
    private String shortName;

    @Column(length = 7)
    private String color;

    public Long getId() {
        return id;
    }

    public String getEik() {
        return eik;
    }

    public String getName() {
        return name;
    }

    public String getShortName() {
        return shortName;
    }

    public String getColor() {
        return color;
    }
}
