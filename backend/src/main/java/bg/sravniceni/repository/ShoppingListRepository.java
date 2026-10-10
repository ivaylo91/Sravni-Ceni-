package bg.sravniceni.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import bg.sravniceni.domain.ShoppingList;

public interface ShoppingListRepository extends JpaRepository<ShoppingList, Long> {

    @EntityGraph(attributePaths = "items")
    List<ShoppingList> findByUserIdOrderByUpdatedAtDesc(UUID userId);

    @EntityGraph(attributePaths = "items")
    Optional<ShoppingList> findByIdAndUserId(Long id, UUID userId);

    long countByUserId(UUID userId);
}
