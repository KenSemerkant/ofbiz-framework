package product.domain;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * Category tree read model (P3-F1-S1): assembled from a root category by
 * fetching children level by level. Cycle detection is path-based — the
 * ancestor path is tracked during descent so a shared descendant under two
 * different roots is not a false positive.
 */
public record CategoryNode(Category category, List<CategoryNode> children) {

    public CategoryNode {
        if (category == null) {
            throw new IllegalArgumentException("category must not be null");
        }
        children = children == null ? List.of() : List.copyOf(children);
    }

    /** Builds the subtree under root; throws on a cycle in the parent graph. */
    public static CategoryNode build(Category root, Function<String, List<Category>> childrenOf) {
        return build(root, childrenOf, new HashSet<>());
    }

    private static CategoryNode build(Category root, Function<String, List<Category>> childrenOf,
            Set<String> ancestors) {
        if (!ancestors.add(root.productCategoryId())) {
            throw new IllegalStateException(
                "category cycle detected at " + root.productCategoryId());
        }
        List<Category> children = childrenOf.apply(root.productCategoryId());
        List<CategoryNode> nodes = (children == null ? List.<Category>of() : children).stream()
            .map(child -> build(child, childrenOf, ancestors))
            .toList();
        ancestors.remove(root.productCategoryId());
        return new CategoryNode(root, nodes);
    }
}
