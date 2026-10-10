package bg.sravniceni.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Населено място по ЕКАТТЕ. */
@Entity
@Table(name = "settlement")
public class Settlement {

    @Id
    @Column(length = 5)
    private String code;

    @Column(nullable = false, length = 120)
    private String name;

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }
}
