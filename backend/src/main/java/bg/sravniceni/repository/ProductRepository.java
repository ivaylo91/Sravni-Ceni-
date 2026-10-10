package bg.sravniceni.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import bg.sravniceni.domain.Product;

public interface ProductRepository extends JpaRepository<Product, Long> {

    @Query("select p from Product p join fetch p.chain join fetch p.category where p.id = :id")
    Optional<Product> findDetailedById(Long id);
}
