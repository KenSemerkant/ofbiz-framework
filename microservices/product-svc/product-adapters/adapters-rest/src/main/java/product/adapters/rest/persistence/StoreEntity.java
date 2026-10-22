package product.adapters.rest.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "product_stores")
public class StoreEntity {

    @Id
    @Column(name = "product_store_id")
    private String productStoreId;

    @Column(name = "store_name", nullable = false)
    private String storeName;

    @Column(name = "default_currency_uom_id")
    private String defaultCurrencyUomId;

    @Column(name = "inventory_facility_id")
    private String inventoryFacilityId;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public StoreEntity() {
    }

    public StoreEntity(String productStoreId, String storeName, String defaultCurrencyUomId,
            String inventoryFacilityId) {
        this.productStoreId = productStoreId;
        this.storeName = storeName;
        this.defaultCurrencyUomId = defaultCurrencyUomId;
        this.inventoryFacilityId = inventoryFacilityId;
    }

    public String getProductStoreId() {
        return productStoreId;
    }

    public String getStoreName() {
        return storeName;
    }

    public String getDefaultCurrencyUomId() {
        return defaultCurrencyUomId;
    }

    public String getInventoryFacilityId() {
        return inventoryFacilityId;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
