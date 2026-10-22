package inventory.domain;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * A shipment of goods (P3-F2-S3): CREATED → PACKED → SHIPPED, forward-only —
 * an invalid transition is a business conflict (InvalidTransitionException).
 * Items can only be added while CREATED. ship() is where the linked order's
 * reservations confirm and the goods leave QOH (application layer).
 */
public final class Shipment {

    public enum Status { CREATED, PACKED, SHIPPED }

    private final String shipmentId;
    private final String orderId;
    private final List<ShipmentItem> items = new ArrayList<>();
    private Status status = Status.CREATED;

    public Shipment(String shipmentId, String orderId) {
        if (shipmentId == null || shipmentId.isBlank()) {
            throw new IllegalArgumentException("shipmentId must not be blank");
        }
        if (orderId != null && orderId.isBlank()) {
            throw new IllegalArgumentException("orderId must not be blank");
        }
        this.shipmentId = shipmentId;
        this.orderId = orderId;
    }

    /** Adds an item; the seqId is assigned in insertion order (OFBiz style). */
    public ShipmentItem addShipmentItem(String productId, BigDecimal quantity,
            String inventoryItemId) {
        if (status != Status.CREATED) {
            throw new InvalidTransitionException(
                "items can only be added while the shipment is CREATED (is " + status + ")");
        }
        ShipmentItem item = new ShipmentItem(String.format("%05d", items.size() + 1), productId,
            quantity, inventoryItemId);
        items.add(item);
        return item;
    }

    public void pack() {
        transition(Status.PACKED);
    }

    public void ship() {
        transition(Status.SHIPPED);
    }

    private void transition(Status target) {
        if (target.ordinal() != status.ordinal() + 1) {
            throw new InvalidTransitionException(
                "cannot transition shipment from " + status + " to " + target);
        }
        status = target;
    }

    /** Rehydration factory: persisted status and items are trusted. */
    public static Shipment rehydrate(String shipmentId, String orderId, Status status,
            List<ShipmentItem> items) {
        Shipment shipment = new Shipment(shipmentId, orderId);
        shipment.status = status;
        shipment.items.addAll(items);
        return shipment;
    }

    public String shipmentId() {
        return shipmentId;
    }

    /** The order this shipment fulfills — id owned by order-svc, may be null. */
    public String orderId() {
        return orderId;
    }

    public Status status() {
        return status;
    }

    public List<ShipmentItem> items() {
        return List.copyOf(items);
    }
}
