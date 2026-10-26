package product.application;

import java.util.Optional;
import product.domain.Category;
import product.domain.CategoryNode;

/**
 * P3-F1-S1 category read model: upsert emits `product.category.updated.v1`;
 * tree queries assemble the subtree from the primary-parent pointers. Delete
 * (spec-review fix): only a leaf without member products can be deleted —
 * no event (category delete is not in the topic set).
 */
public class CategoryService {

    public static final String CATEGORY_UPDATED = "product.category.updated.v1";

    private final CategoryStore store;
    private final ProductStore products;
    private final OutboxWriter outbox;
    private final CategorySerializer serializer;

    public CategoryService(CategoryStore store, ProductStore products, OutboxWriter outbox,
            CategorySerializer serializer) {
        this.store = store;
        this.products = products;
        this.outbox = outbox;
        this.serializer = serializer;
    }

    /** Outcome of a category delete attempt (spec-review fix). */
    public enum DeleteResult {
        DELETED, NOT_FOUND, NOT_LEAF
    }

    public Optional<Category> get(String productCategoryId) {
        return store.find(productCategoryId);
    }

    /** P9-F2: category listing for the SPA catalog area. */
    public java.util.List<Category> all() {
        return store.all();
    }



    public void upsert(Category category) {
        store.save(category);
        outbox.append(CATEGORY_UPDATED, category.productCategoryId(),
            serializer.serialize(category));
    }

    /** Category tree query (P3-F1-S1 AC); empty when the root is unknown. */
    public Optional<CategoryNode> tree(String productCategoryId) {
        return store.find(productCategoryId)
            .map(root -> CategoryNode.build(root, store::findChildren));
    }

    public DeleteResult delete(String productCategoryId) {
        if (store.find(productCategoryId).isEmpty()) {
            return DeleteResult.NOT_FOUND;
        }
        if (!store.findChildren(productCategoryId).isEmpty()
                || products.anyInCategory(productCategoryId)) {
            return DeleteResult.NOT_LEAF;
        }
        store.delete(productCategoryId);
        return DeleteResult.DELETED;
    }
}
