package marketing.adapters.rest.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

/** attributions row (P6-T5): one coded order, unique on order_id. */
@Entity
@Table(name = "attributions")
public class AttributionEntity {

    @Id
    @Column(name = "order_id")
    private String orderId;

    @Column(name = "tracking_code", nullable = false)
    private String trackingCode;

    @Column(name = "order_total", nullable = false)
    private BigDecimal orderTotal;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    public AttributionEntity() {
    }

    public AttributionEntity(String orderId, String trackingCode, BigDecimal orderTotal,
            Instant occurredAt) {
        this.orderId = orderId;
        this.trackingCode = trackingCode;
        this.orderTotal = orderTotal;
        this.occurredAt = occurredAt;
    }

    public String getOrderId() {
        return orderId;
    }

    public String getTrackingCode() {
        return trackingCode;
    }

    public BigDecimal getOrderTotal() {
        return orderTotal;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
