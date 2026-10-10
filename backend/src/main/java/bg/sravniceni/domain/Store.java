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

/** Търговски обект (конкретен магазин на верига). */
@Entity
@Table(name = "store")
public class Store {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "chain_id")
    private Chain chain;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "settlement_code")
    private Settlement settlement;

    @Column(nullable = false, length = 500)
    private String address;

    public Long getId() {
        return id;
    }

    public Chain getChain() {
        return chain;
    }

    public Settlement getSettlement() {
        return settlement;
    }

    public String getAddress() {
        return address;
    }
}
