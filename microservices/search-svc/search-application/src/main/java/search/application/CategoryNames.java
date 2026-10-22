package search.application;

import java.util.Collection;
import java.util.Map;

/**
 * Category-name projection fed by product.category.updated.v1 — the product
 * events only carry category IDs, so names are resolved from local state
 * (no cross-service calls; ADR-007/ADR-009 event-carried state).
 */
public interface CategoryNames {

    void save(String categoryId, String name);

    /** @return id -> name for every known id; unknown ids are absent. */
    Map<String, String> resolve(Collection<String> categoryIds);
}
