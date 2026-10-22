package product.application;

import java.util.List;
import java.util.Optional;
import product.domain.Category;

/** Persistence port for the category read model. */
public interface CategoryStore {

    Optional<Category> find(String productCategoryId);

    void save(Category category);

    /** Direct children of a category, ordered by category id. */
    List<Category> findChildren(String productCategoryId);

    void delete(String productCategoryId);
}
