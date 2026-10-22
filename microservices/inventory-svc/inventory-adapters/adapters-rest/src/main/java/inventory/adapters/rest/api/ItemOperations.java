package inventory.adapters.rest.api;

import java.math.BigDecimal;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import inventory.application.InventoryService;
import inventory.domain.InventoryItem;

/**
 * Adapter-layer transaction boundary for stock ops (ADR-009), mirroring
 * ReservationOperations: each call is ONE transaction spanning the item
 * mutation(s) and the outbox event(s). A separate bean so the controller can
 * retry the whole transaction on optimistic-lock conflicts — concurrent
 * stock ops get the same 409-instead-of-500 treatment as reservations
 * (quality review).
 */
@Component
public class ItemOperations {

    private final InventoryService inventoryService;

    public ItemOperations(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @Transactional
    public InventoryItem create(InventoryItem item) {
        return inventoryService.create(item);
    }

    @Transactional
    public InventoryItem receive(String inventoryItemId, BigDecimal quantity) {
        return inventoryService.receive(inventoryItemId, quantity);
    }

    @Transactional
    public InventoryItem adjust(String inventoryItemId, BigDecimal quantity) {
        return inventoryService.adjust(inventoryItemId, quantity);
    }

    @Transactional
    public InventoryItem transfer(String fromItemId, String toItemId, BigDecimal quantity) {
        return inventoryService.transfer(fromItemId, toItemId, quantity);
    }
}
