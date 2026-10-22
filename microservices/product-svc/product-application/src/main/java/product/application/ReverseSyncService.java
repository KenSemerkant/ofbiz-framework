package product.application;

import java.util.List;
import java.util.Set;
import product.domain.ProductAssoc;
import product.domain.ProductPrice;

/**
 * P3-GATE: applies `product.product.created/updated/deleted.v1` and
 * `product.category.updated.v1` back to the monolith. Conflict policy:
 * new-service wins (ADR-012) — the event payload is the source of truth
 * and overwrites the monolith rows. Price events need no separate
 * handling: every created/updated payload carries the full price set, so
 * the set-sync in the writer converges prices between events.
 */
public class ReverseSyncService {

    private final MonolithSyncWriter monolith;

    public ReverseSyncService(MonolithSyncWriter monolith) {
        this.monolith = monolith;
    }

    public record ProductSnapshot(String productId, String productTypeId, String internalName,
            String productName, Set<String> categoryIds, List<ProductPrice> prices,
            List<ProductAssoc> assocs) {
    }

    public void onProductEvent(ProductSnapshot snapshot) {
        monolith.syncProduct(snapshot.productId(), snapshot.productTypeId(),
            snapshot.internalName(), snapshot.productName(), snapshot.categoryIds(),
            snapshot.prices(), snapshot.assocs());
    }

    public void onCategoryEvent(String productCategoryId, String categoryName) {
        monolith.syncCategory(productCategoryId, categoryName);
    }

    public void onProductDeleted(String productId) {
        monolith.deleteProduct(productId);
    }
}
