package order.application;

import java.math.BigDecimal;

/**
 * Outbound port to inventory-svc: reserve/release for the placeOrder saga
 * (P4-F2-S1), stock receive-back for returns (P4-F1-S3).
 */
public interface InventoryGateway {

    /** Reserve receipt kept for compensation and return receive-back. */
    record ReservationReceipt(String reservationId, String inventoryItemId, String productId,
            String facilityId) {
    }

    /** @throws InventoryUnavailableException when stock cannot cover the line. */
    ReservationReceipt reserve(String orderId, String orderItemSeqId, String productId,
            String facilityId, BigDecimal quantity);

    /** Compensation (P4-F2-S1): give reserved ATP back. */
    void release(String reservationId);

    /** Return adjustment (P4-F1-S3): put returned stock back on the shelf. */
    void receive(String inventoryItemId, BigDecimal quantity);
}
