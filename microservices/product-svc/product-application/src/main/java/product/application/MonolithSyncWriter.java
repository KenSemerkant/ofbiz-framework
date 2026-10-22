package product.application;

import java.util.List;
import java.util.Set;
import product.domain.ProductAssoc;
import product.domain.ProductPrice;

/**
 * Reverse-sync port (P3-GATE, ADR-012): writes product-svc state back to
 * the monolith tables for not-yet-migrated consumers.
 */
public interface MonolithSyncWriter {

    /**
     * Upsert the product across PRODUCT / PRODUCT_CATEGORY_MEMBER /
     * PRODUCT_PRICE / PRODUCT_ASSOC. Conflict policy: the new service wins —
     * every field is overwritten unconditionally; memberships, prices and
     * assocs not in the event are removed (set-sync, like party roles).
     */
    void syncProduct(String productId, String productTypeId, String internalName,
            String productName, Set<String> categoryIds, List<ProductPrice> prices,
            List<ProductAssoc> assocs);

    /** Upsert one PRODUCT_CATEGORY row (name changes only). */
    void syncCategory(String productCategoryId, String categoryName);

    /** Remove the product and its slice rows from the monolith. */
    void deleteProduct(String productId);
}
