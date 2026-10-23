package order.adapters.rest.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

/** orders row: lines + status history inline as JSON (same call as
 *  product-svc's assocs/prices — no cross-line query in any Phase-4 story). */
@Entity
@Table(name = "orders")
public class OrderEntity {

    @Id
    @Column(name = "order_id")
    private String orderId;

    @Column(name = "customer_id", nullable = false)
    private String customerId;

    @Column(name = "tracking_code")
    private String trackingCode;

    @Column(nullable = false)
    private String status;

    @Column(nullable = false)
    private String lines;

    @Column(nullable = false)
    private String history;

    @Column(name = "grand_total", nullable = false, precision = 18, scale = 2)
    private BigDecimal grandTotal;

    @Column(nullable = false)
    private String currencyUomId;

    @Column(name = "placed_at", nullable = false)
    private Instant placedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public OrderEntity() {
    }

    public OrderEntity(String orderId, String customerId, String trackingCode, String status,
            String lines, String history, BigDecimal grandTotal, String currencyUomId,
            Instant placedAt) {
        this.orderId = orderId;
        this.customerId = customerId;
        this.trackingCode = trackingCode;
        this.status = status;
        this.lines = lines;
        this.history = history;
        this.grandTotal = grandTotal;
        this.currencyUomId = currencyUomId;
        this.placedAt = placedAt;
    }

    public String getOrderId() {
        return orderId;
    }

    public String getCustomerId() {
        return customerId;
    }

    public String getTrackingCode() {
        return trackingCode;
    }

    public String getStatus() {
        return status;
    }

    public String getLines() {
        return lines;
    }

    public String getHistory() {
        return history;
    }

    public BigDecimal getGrandTotal() {
        return grandTotal;
    }

    public String getCurrencyUomId() {
        return currencyUomId;
    }

    public Instant getPlacedAt() {
        return placedAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
