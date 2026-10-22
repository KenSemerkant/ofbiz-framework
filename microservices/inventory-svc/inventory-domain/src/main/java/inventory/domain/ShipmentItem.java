package inventory.domain;

import java.math.BigDecimal;

/** One line of a shipment: what ships, how much, from which inventory item. */
public record ShipmentItem(String shipmentItemSeqId, String productId, BigDecimal quantity,
        String inventoryItemId) {

    public ShipmentItem {
        if (shipmentItemSeqId == null || shipmentItemSeqId.isBlank()) {
            throw new IllegalArgumentException("shipmentItemSeqId must not be blank");
        }
        if (productId == null || productId.isBlank()) {
            throw new IllegalArgumentException("productId must not be blank");
        }
        if (inventoryItemId == null || inventoryItemId.isBlank()) {
            throw new IllegalArgumentException("inventoryItemId must not be blank");
        }
        if (quantity == null || quantity.signum() <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }
    }
}
