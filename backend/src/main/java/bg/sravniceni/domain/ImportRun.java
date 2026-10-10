package bg.sravniceni.domain;

import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Запис за един импорт на дневните данни от КЗП. */
@Entity
@Table(name = "import_run")
public class ImportRun {

    public enum Status { RUNNING, SUCCESS, FAILED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "price_date", nullable = false)
    private LocalDate priceDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(nullable = false)
    private int chains;

    @Column(name = "rows_imported", nullable = false)
    private int rowsImported;

    @Column(columnDefinition = "text")
    private String message;

    protected ImportRun() {
    }

    public static ImportRun start(LocalDate priceDate) {
        var run = new ImportRun();
        run.priceDate = priceDate;
        run.status = Status.RUNNING;
        run.startedAt = Instant.now();
        return run;
    }

    public void succeed(int chains, int rows, String warnings) {
        this.status = Status.SUCCESS;
        this.message = warnings;
        this.chains = chains;
        this.rowsImported = rows;
        this.finishedAt = Instant.now();
    }

    public void fail(String message) {
        this.status = Status.FAILED;
        this.message = message;
        this.finishedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public LocalDate getPriceDate() {
        return priceDate;
    }

    public Status getStatus() {
        return status;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public int getChains() {
        return chains;
    }

    public int getRowsImported() {
        return rowsImported;
    }

    public String getMessage() {
        return message;
    }
}
