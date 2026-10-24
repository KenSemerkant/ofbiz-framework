package manufacturing.adapters.rest.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

/** mrp_events row (P6-T1): the append-only supply/demand ledger. */
@Entity
@Table(name = "mrp_events")
public class MrpEventEntity {

    @Id
    @Column(name = "event_id")
    private String eventId;

    @Column(name = "product_id", nullable = false)
    private String productId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false)
    private manufacturing.domain.MrpEventType eventType;

    @Column(nullable = false)
    private BigDecimal quantity;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    public MrpEventEntity() {
    }

    public MrpEventEntity(String eventId, String productId,
            manufacturing.domain.MrpEventType eventType, BigDecimal quantity,
            Instant occurredAt) {
        this.eventId = eventId;
        this.productId = productId;
        this.eventType = eventType;
        this.quantity = quantity;
        this.occurredAt = occurredAt;
    }

    public String getEventId() {
        return eventId;
    }

    public String getProductId() {
        return productId;
    }

    public manufacturing.domain.MrpEventType getEventType() {
        return eventType;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
