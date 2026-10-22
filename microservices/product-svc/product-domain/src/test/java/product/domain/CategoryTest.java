package product.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** P3-F1-S1 category read model: identity guards and tree assembly. */
class CategoryTest {

    @Test
    void categoryRequiresIdAndName() {
        Category cat = new Category("CAT", null, "Widgets");
        assertEquals("CAT", cat.productCategoryId());
        assertEquals("Widgets", cat.categoryName());
        assertThrows(IllegalArgumentException.class, () -> new Category(" ", "P", "n"));
        assertThrows(IllegalArgumentException.class, () -> new Category("CAT", "P", " "));
    }

    @Test
    void treeAssemblesRecursively() {
        Map<String, List<Category>> children = Map.of(
            "ROOT", List.of(new Category("A", "ROOT", "A")),
            "A", List.of(new Category("B", "A", "B")));
        CategoryNode tree = CategoryNode.build(new Category("ROOT", null, "Root"), children::get);
        assertEquals("ROOT", tree.category().productCategoryId());
        assertEquals(1, tree.children().size());
        assertEquals("B", tree.children().get(0).children().get(0).category().productCategoryId());
    }

    @Test
    void cycleIsRejected() {
        Map<String, List<Category>> children = Map.of(
            "ROOT", List.of(new Category("A", "ROOT", "A")),
            "A", List.of(new Category("ROOT", "A", "loop")));
        assertThrows(IllegalStateException.class,
            () -> CategoryNode.build(new Category("ROOT", null, "Root"), children::get));
    }

    @Test
    void nullChildrenAreTolerated() {
        CategoryNode node = new CategoryNode(new Category("CAT", null, "n"), null);
        assertEquals(List.of(), node.children());
        assertThrows(IllegalArgumentException.class, () -> new CategoryNode(null, List.of()));
    }
}
