package billing.adapters.rest.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

/** invoices row: lines + status history inline as JSON (order-svc call). */
@Entity
@Table(name = "invoices")
public class InvoiceEntity {

    @Id
    @Column(name = "invoice_id")
    private String invoiceId;

    @Column(name = "order_id")
    private String orderId;

    @Column(name = "customer_id", nullable = false)
    private String customerId;

    @Column(name = "billing_account_id")
    private String billingAccountId;

    @Column(nullable = false)
    private String status;

    @Column(nullable = false)
    private String lines;

    @Column(nullable = false)
    private String history;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal total;

    @Column(name = "currency_uom_id", nullable = false)
    private String currencyUomId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public InvoiceEntity() {
    }

    public InvoiceEntity(String invoiceId, String orderId, String customerId,
            String billingAccountId, String status, String lines, String history,
            BigDecimal total, String currencyUomId, Instant createdAt) {
        this.invoiceId = invoiceId;
        this.orderId = orderId;
        this.customerId = customerId;
        this.billingAccountId = billingAccountId;
        this.status = status;
        this.lines = lines;
        this.history = history;
        this.total = total;
        this.currencyUomId = currencyUomId;
        this.createdAt = createdAt;
    }

    public String getInvoiceId() {
        return invoiceId;
    }

    public String getOrderId() {
        return orderId;
    }

    public String getCustomerId() {
        return customerId;
    }

    public String getBillingAccountId() {
        return billingAccountId;
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

    public BigDecimal getTotal() {
        return total;
    }

    public String getCurrencyUomId() {
        return currencyUomId;
    }
}
