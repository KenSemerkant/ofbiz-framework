package inventory.application;

import java.util.NoSuchElementException;
import java.util.Optional;
import inventory.domain.Shipment;

/**
 * P3-F2-S3 use case: shipment creation/packing/shipping. Every transition
 * emits `inventory.shipment.updated.v1`; ship() additionally confirms the
 * linked order's live reservations (each decrements QOH on its item) and
 * emits `inventory.stock.shipped.v1` — all in the caller's transaction.
 */
public class ShipmentService {

    public static final String SHIPMENT_UPDATED = "inventory.shipment.updated.v1";
    public static final String STOCK_SHIPPED = "inventory.stock.shipped.v1";

    private final ShipmentStore shipments;
    private final ReservationService reservations;
    private final OutboxWriter outbox;
    private final InventorySerializer serializer;

    public ShipmentService(ShipmentStore shipments, ReservationService reservations,
            OutboxWriter outbox, InventorySerializer serializer) {
        this.shipments = shipments;
        this.reservations = reservations;
        this.outbox = outbox;
        this.serializer = serializer;
    }

    public Optional<Shipment> get(String shipmentId) {
        return shipments.find(shipmentId);
    }

    public Shipment create(Shipment shipment) {
        if (shipments.find(shipment.shipmentId()).isPresent()) {
            throw new DuplicateException("shipment already exists: " + shipment.shipmentId());
        }
        shipments.save(shipment);
        outbox.append(SHIPMENT_UPDATED, shipment.shipmentId(),
            serializer.serializeShipment(shipment));
        return shipment;
    }

    public Shipment pack(String shipmentId) {
        Shipment shipment = load(shipmentId);
        shipment.pack();
        shipments.save(shipment);
        outbox.append(SHIPMENT_UPDATED, shipmentId, serializer.serializeShipment(shipment));
        return shipment;
    }

    public Shipment ship(String shipmentId) {
        Shipment shipment = load(shipmentId);
        shipment.ship();
        if (shipment.orderId() != null) {
            // the goods leave: every live reservation of the order confirms
            // (QOH decrement + stock.confirmed events) in this transaction
            reservations.confirmOrderReservations(shipment.orderId());
        }
        shipments.save(shipment);
        outbox.append(SHIPMENT_UPDATED, shipmentId, serializer.serializeShipment(shipment));
        outbox.append(STOCK_SHIPPED, shipmentId, serializer.serializeShipment(shipment));
        return shipment;
    }

    private Shipment load(String shipmentId) {
        return shipments.find(shipmentId).orElseThrow(
            () -> new NoSuchElementException("no shipment " + shipmentId));
    }
}
