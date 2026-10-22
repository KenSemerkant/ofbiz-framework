package product.adapters.rest.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryRepository extends JpaRepository<CategoryEntity, String> {

    List<CategoryEntity> findByPrimaryParentCategoryIdOrderByProductCategoryId(
        String primaryParentCategoryId);
}
