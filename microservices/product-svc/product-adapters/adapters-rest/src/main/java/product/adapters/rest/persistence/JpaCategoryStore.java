package product.adapters.rest.persistence;

import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import product.application.CategoryStore;
import product.domain.Category;

/** JPA adapter for the CategoryStore port. */
@Repository
public class JpaCategoryStore implements CategoryStore {

    private final CategoryRepository categories;

    public JpaCategoryStore(CategoryRepository categories) {
        this.categories = categories;
    }

    @Override
    public Optional<Category> find(String productCategoryId) {
        return categories.findById(productCategoryId)
            .map(e -> new Category(e.getProductCategoryId(), e.getPrimaryParentCategoryId(),
                e.getCategoryName()));
    }

    @Override
    public void save(Category category) {
        categories.save(new CategoryEntity(category.productCategoryId(),
            category.primaryParentCategoryId(), category.categoryName()));
    }

    @Override
    public List<Category> findChildren(String productCategoryId) {
        return categories
            .findByPrimaryParentCategoryIdOrderByProductCategoryId(productCategoryId).stream()
            .map(e -> new Category(e.getProductCategoryId(), e.getPrimaryParentCategoryId(),
                e.getCategoryName()))
            .toList();
    }
}
