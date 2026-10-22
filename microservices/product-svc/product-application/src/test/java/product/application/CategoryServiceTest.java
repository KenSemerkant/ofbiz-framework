package product.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import product.domain.Category;
import product.domain.CategoryNode;

/** AC: category upsert emits product.category.updated; tree query assembles. */
class CategoryServiceTest {

    private final Map<String, Category> storeData = new HashMap<>();
    private final List<String> outboxEvents = new ArrayList<>();

    private final CategoryService service = new CategoryService(
        new CategoryStore() {
            @Override
            public Optional<Category> find(String productCategoryId) {
                return Optional.ofNullable(storeData.get(productCategoryId));
            }

            @Override
            public void save(Category category) {
                storeData.put(category.productCategoryId(), category);
            }

            @Override
            public List<Category> findChildren(String productCategoryId) {
                return storeData.values().stream()
                    .filter(c -> productCategoryId.equals(c.primaryParentCategoryId()))
                    .sorted((a, b) -> a.productCategoryId().compareTo(b.productCategoryId()))
                    .toList();
            }

            @Override
            public void delete(String productCategoryId) {
                storeData.remove(productCategoryId);
            }
        },
        new ProductStore() {
            @Override
            public Optional<product.domain.Product> find(String productId) {
                return Optional.empty();
            }

            @Override
            public void save(product.domain.Product product) {
            }

            @Override
            public boolean delete(String productId) {
                return false;
            }

            @Override
            public boolean anyInCategory(String categoryId) {
                return "MEMBERED".equals(categoryId);
            }
        },
        (eventType, aggregateKey, payload) -> outboxEvents.add(eventType));

    @Test
    void upsertEmitsCategoryUpdated() {
        service.upsert(new Category("CAT", null, "Widgets"));
        assertEquals(List.of(CategoryService.CATEGORY_UPDATED), outboxEvents);
        assertEquals("Widgets", service.get("CAT").orElseThrow().categoryName());
    }

    @Test
    void treeAssemblesFromPrimaryParents() {
        service.upsert(new Category("ROOT", null, "Root"));
        service.upsert(new Category("A", "ROOT", "A"));
        service.upsert(new Category("B", "A", "B"));

        CategoryNode tree = service.tree("ROOT").orElseThrow();
        assertEquals("ROOT", tree.category().productCategoryId());
        assertEquals(1, tree.children().size());
        assertEquals("B", tree.children().get(0).children().get(0)
            .category().productCategoryId());
    }

    @Test
    void unknownRootTreeIsEmpty() {
        assertTrue(service.tree("nope").isEmpty());
    }

    @Test
    void deleteOnlyWhenLeafWithoutMembers() {
        assertEquals(CategoryService.DeleteResult.NOT_FOUND, service.delete("nope"));

        service.upsert(new Category("ROOT", null, "Root"));
        service.upsert(new Category("CHILD", "ROOT", "Child"));
        assertEquals(CategoryService.DeleteResult.NOT_LEAF, service.delete("ROOT"));
        assertEquals(CategoryService.DeleteResult.DELETED, service.delete("CHILD"));
        assertTrue(service.get("CHILD").isEmpty());

        service.upsert(new Category("MEMBERED", null, "Has products"));
        assertEquals(CategoryService.DeleteResult.NOT_LEAF, service.delete("MEMBERED"));
    }
}
