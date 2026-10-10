package bg.sravniceni.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import bg.sravniceni.domain.Category;

public interface CategoryRepository extends JpaRepository<Category, Short> {

    @Query("select c from Category c join fetch c.group g order by g.sortOrder, c.id")
    List<Category> findAllWithGroup();
}
