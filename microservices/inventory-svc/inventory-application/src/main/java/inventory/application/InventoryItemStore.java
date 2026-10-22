package inventory.application;

import java.util.List;
import java.util.Optional;
import inventory.domain.InventoryItem;

/** Persistence port for the InventoryItem aggregate. */
public interface InventoryItemStore {

    Optional<InventoryItem> find(String inventoryItemId);

    /** Items holding a product at a facility, in stable row order (P3-F2-S2). */
    List<InventoryItem> findByProductIdAndFacilityId(String productId, String facilityId);

    void save(InventoryItem item);
}
