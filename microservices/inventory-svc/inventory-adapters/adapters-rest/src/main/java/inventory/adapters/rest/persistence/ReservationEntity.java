package inventory.adapters.rest.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import inventory.domain.Reservation;

@Entity
@Table(name = "reservations")
public class ReservationEntity {

    @Id
    @Column(name = "reservation_id")
    private String reservationId;

    @Column(name = "order_id", nullable = false)
    private String orderId;

    @Column(name = "order_item_seq_id", nullable = false)
    private String orderItemSeqId;

    @Column(name = "product_id", nullable = false)
    private String productId;

    @Column(name = "facility_id", nullable = false)
    private String facilityId;

    @Column(name = "inventory_item_id", nullable = false)
    private String inventoryItemId;

    @Column(name = "quantity", nullable = false, precision = 18, scale = 3)
    private BigDecimal quantity;

    @Column(nullable = false)
    private String status;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public ReservationEntity() {
    }

    public ReservationEntity(String reservationId) {
        this.reservationId = reservationId;
    }

    /** Copies aggregate state onto the row. */
    public void applyFrom(Reservation reservation) {
        this.orderId = reservation.orderId();
        this.orderItemSeqId = reservation.orderItemSeqId();
        this.productId = reservation.productId();
        this.facilityId = reservation.facilityId();
        this.inventoryItemId = reservation.inventoryItemId();
        this.quantity = reservation.quantity();
        this.status = reservation.status().name();
        this.updatedAt = Instant.now();
    }

    public Reservation toDomain() {
        return Reservation.rehydrate(reservationId, orderId, orderItemSeqId, productId,
            facilityId, inventoryItemId, quantity, Reservation.Status.valueOf(status));
    }

    public String getReservationId() {
        return reservationId;
    }

    public String getOrderId() {
        return orderId;
    }

    public String getOrderItemSeqId() {
        return orderItemSeqId;
    }

    public String getProductId() {
        return productId;
    }

    public String getFacilityId() {
        return facilityId;
    }

    public String getInventoryItemId() {
        return inventoryItemId;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public String getStatus() {
        return status;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
