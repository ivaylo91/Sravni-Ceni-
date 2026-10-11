package bg.sravniceni.importer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

class RunOnceImportTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 11);

    @Test
    void parsesYesterdaySingleDateAndRange() {
        assertThat(RunOnceImport.parse("yesterday", TODAY)).containsExactly(LocalDate.of(2026, 10, 10));
        assertThat(RunOnceImport.parse("2026-10-09", TODAY)).containsExactly(LocalDate.of(2026, 10, 9));
        assertThat(RunOnceImport.parse("2026-09-29..2026-10-01", TODAY)).containsExactly(
                LocalDate.of(2026, 9, 29), LocalDate.of(2026, 9, 30), LocalDate.of(2026, 10, 1));
    }

    @Test
    void rejectsReversedRange() {
        assertThatThrownBy(() -> RunOnceImport.parse("2026-10-02..2026-10-01", TODAY))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
