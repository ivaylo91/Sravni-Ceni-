package bg.sravniceni.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import bg.sravniceni.domain.Chain;

public interface ChainRepository extends JpaRepository<Chain, Long> {
}
