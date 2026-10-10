package bg.sravniceni.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import bg.sravniceni.domain.ImportRun;

public interface ImportRunRepository extends JpaRepository<ImportRun, Long> {

    List<ImportRun> findTop30ByOrderByStartedAtDesc();

    Optional<ImportRun> findTopByStatusOrderByPriceDateDesc(ImportRun.Status status);

    boolean existsByStatus(ImportRun.Status status);
}
