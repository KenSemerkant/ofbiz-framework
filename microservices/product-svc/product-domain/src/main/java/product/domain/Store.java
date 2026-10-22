package product.domain;

/**
 * A product store and its key settings (OFBiz ProductStore slice, P3-F1-S3):
 * display name, default pricing currency and the facility fulfilling its
 * inventory (an id owned by inventory-svc — reference only, ADR-007).
 */
public record Store(String productStoreId, String storeName, String defaultCurrencyUomId,
        String inventoryFacilityId) {

    public Store {
        if (productStoreId == null || productStoreId.isBlank()) {
            throw new IllegalArgumentException("productStoreId must not be blank");
        }
        if (storeName == null || storeName.isBlank()) {
            throw new IllegalArgumentException("storeName must not be blank");
        }
        if (defaultCurrencyUomId != null && defaultCurrencyUomId.isBlank()) {
            throw new IllegalArgumentException("defaultCurrencyUomId must not be blank");
        }
        if (inventoryFacilityId != null && inventoryFacilityId.isBlank()) {
            throw new IllegalArgumentException("inventoryFacilityId must not be blank");
        }
    }
}
