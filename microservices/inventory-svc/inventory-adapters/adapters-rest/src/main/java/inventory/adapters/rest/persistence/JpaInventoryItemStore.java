package inventory.adapters.rest.persistence;

import java.util.Optional;
import org.springframework.stereotype.Repository;
import inventory.application.InventoryItemStore;
import inventory.domain.InventoryItem;

/**
 * JPA adapter for the InventoryItemStore port. save() copies onto the row
 * loaded in the caller's transaction, so the @Version column participates:
 * a concurrent mutation makes the flush fail with an optimistic-lock error
 * instead of last-write-wins (P3-F2-S2 oversell prevention).
 */
@Repository
public class JpaInventoryItemStore implements InventoryItemStore {

    private final InventoryItemRepository items;

    public JpaInventoryItemStore(InventoryItemRepository items) {
        this.items = items;
    }

    @Override
    public Optional<InventoryItem> find(String inventoryItemId) {
        return items.findById(inventoryItemId).map(InventoryItemEntity::toDomain);
    }

    @Override
    public void save(InventoryItem item) {
        InventoryItemEntity entity = items.findById(item.inventoryItemId())
            .orElseGet(() -> new InventoryItemEntity(item.inventoryItemId()));
        entity.applyFrom(item);
        items.save(entity);
    }
}
