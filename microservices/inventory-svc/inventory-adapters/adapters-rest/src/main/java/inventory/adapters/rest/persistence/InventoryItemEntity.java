package inventory.adapters.rest.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import inventory.domain.InventoryItem;

@Entity
@Table(name = "inventory_items")
public class InventoryItemEntity {

    @Id
    @Column(name = "inventory_item_id")
    private String inventoryItemId;

    @Column(name = "product_id", nullable = false)
    private String productId;

    @Column(name = "facility_id", nullable = false)
    private String facilityId;

    @Column(name = "serial_number")
    private String serialNumber;

    @Column(name = "unit_cost", precision = 18, scale = 2)
    private BigDecimal unitCost;

    @Column(name = "quantity_on_hand_total", nullable = false, precision = 18, scale = 3)
    private BigDecimal quantityOnHandTotal;

    @Column(name = "available_to_promise_total", nullable = false, precision = 18, scale = 3)
    private BigDecimal availableToPromiseTotal;

    /**
     * Optimistic concurrency (P3-F2-S2): concurrent stock mutations conflict
     * at flush time instead of silently overwriting each other's totals.
     */
    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public InventoryItemEntity() {
    }

    public InventoryItemEntity(String inventoryItemId) {
        this.inventoryItemId = inventoryItemId;
    }

    /** Copies aggregate state onto the row; the version is Hibernate's, not ours. */
    public void applyFrom(InventoryItem item) {
        this.productId = item.productId();
        this.facilityId = item.facilityId();
        this.serialNumber = item.serialNumber();
        this.unitCost = item.unitCost();
        this.quantityOnHandTotal = item.quantityOnHandTotal();
        this.availableToPromiseTotal = item.availableToPromiseTotal();
        this.updatedAt = Instant.now();
    }

    public InventoryItem toDomain() {
        return new InventoryItem(inventoryItemId, productId, facilityId, serialNumber, unitCost,
            quantityOnHandTotal, availableToPromiseTotal);
    }

    public String getInventoryItemId() {
        return inventoryItemId;
    }

    public String getProductId() {
        return productId;
    }

    public String getFacilityId() {
        return facilityId;
    }

    public String getSerialNumber() {
        return serialNumber;
    }

    public BigDecimal getUnitCost() {
        return unitCost;
    }

    public BigDecimal getQuantityOnHandTotal() {
        return quantityOnHandTotal;
    }

    public BigDecimal getAvailableToPromiseTotal() {
        return availableToPromiseTotal;
    }

    public long getVersion() {
        return version;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
