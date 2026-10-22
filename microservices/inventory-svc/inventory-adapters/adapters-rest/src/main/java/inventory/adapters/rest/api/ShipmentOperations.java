package inventory.adapters.rest.api;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import inventory.application.ShipmentService;
import inventory.domain.Shipment;

/**
 * Adapter-layer transaction boundary for the ship-order flow (ADR-009):
 * pack/ship span the shipment row, the linked reservations' confirmations,
 * the inventory item QOH decrements and the outbox events in ONE transaction.
 * Separate bean so the controller can retry the whole transaction on
 * optimistic-lock conflicts (ship writes the same hot item rows reserves do).
 */
@Component
public class ShipmentOperations {

    private final ShipmentService shipments;

    public ShipmentOperations(ShipmentService shipments) {
        this.shipments = shipments;
    }

    @Transactional
    public Shipment create(Shipment shipment) {
        return shipments.create(shipment);
    }

    @Transactional
    public Shipment pack(String shipmentId) {
        return shipments.pack(shipmentId);
    }

    @Transactional
    public Shipment ship(String shipmentId) {
        return shipments.ship(shipmentId);
    }
}
