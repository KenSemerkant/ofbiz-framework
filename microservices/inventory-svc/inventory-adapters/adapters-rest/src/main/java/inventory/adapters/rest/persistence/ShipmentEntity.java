package inventory.adapters.rest.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import inventory.domain.Shipment;

@Entity
@Table(name = "shipments")
public class ShipmentEntity {

    @Id
    @Column(name = "shipment_id")
    private String shipmentId;

    /** Order this shipment fulfills — id owned by order-svc, no FK (ADR-007). */
    @Column(name = "order_id")
    private String orderId;

    @Column(nullable = false)
    private String status;

    @Column(nullable = false)
    private String items;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public ShipmentEntity() {
    }

    public ShipmentEntity(String shipmentId) {
        this.shipmentId = shipmentId;
    }

    /** Copies aggregate state onto the row. */
    public void applyFrom(Shipment shipment) {
        this.orderId = shipment.orderId();
        this.status = shipment.status().name();
        this.items = ShipmentJson.serializeItems(shipment.items());
        this.updatedAt = Instant.now();
    }

    public Shipment toDomain() {
        return Shipment.rehydrate(shipmentId, orderId, Shipment.Status.valueOf(status),
            ShipmentJson.deserializeItems(items));
    }

    public String getShipmentId() {
        return shipmentId;
    }

    public String getOrderId() {
        return orderId;
    }

    public String getStatus() {
        return status;
    }

    public String getItems() {
        return items;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
