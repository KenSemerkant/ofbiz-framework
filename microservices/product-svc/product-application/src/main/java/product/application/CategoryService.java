package product.application;

import java.util.Optional;
import product.domain.Category;
import product.domain.CategoryNode;

/**
 * P3-F1-S1 category read model: upsert emits `product.category.updated.v1`;
 * tree queries assemble the subtree from the primary-parent pointers.
 */
public class CategoryService {

    public static final String CATEGORY_UPDATED = "product.category.updated.v1";

    private final CategoryStore store;
    private final OutboxWriter outbox;

    public CategoryService(CategoryStore store, OutboxWriter outbox) {
        this.store = store;
        this.outbox = outbox;
    }

    public Optional<Category> get(String productCategoryId) {
        return store.find(productCategoryId);
    }

    public void upsert(Category category) {
        store.save(category);
        outbox.append(CATEGORY_UPDATED, category.productCategoryId(), payload(category));
    }

    /** Category tree query (P3-F1-S1 AC); empty when the root is unknown. */
    public Optional<CategoryNode> tree(String productCategoryId) {
        return store.find(productCategoryId)
            .map(root -> CategoryNode.build(root, store::findChildren));
    }

    private String payload(Category category) {
        String parent = category.primaryParentCategoryId() == null
            ? null
            : "\"" + category.primaryParentCategoryId() + "\"";
        return "{\"productCategoryId\":\"" + category.productCategoryId()
            + "\",\"categoryName\":\"" + category.categoryName()
            + "\",\"primaryParentCategoryId\":" + parent + "}";
    }
}
