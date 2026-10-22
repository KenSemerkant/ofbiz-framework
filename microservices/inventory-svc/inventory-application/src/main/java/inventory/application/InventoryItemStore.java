package inventory.application;

import java.util.Optional;
import inventory.domain.InventoryItem;

/** Persistence port for the InventoryItem aggregate. */
public interface InventoryItemStore {

    Optional<InventoryItem> find(String inventoryItemId);

    void save(InventoryItem item);
}
