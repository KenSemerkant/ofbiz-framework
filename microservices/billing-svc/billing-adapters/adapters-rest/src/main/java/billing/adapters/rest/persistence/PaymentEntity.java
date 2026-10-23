package billing.adapters.rest.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

/** payments row (P5-F1-S2): idempotency_key is enforced UNIQUE. */
@Entity
@Table(name = "payments")
public class PaymentEntity {

    @Id
    @Column(name = "payment_id")
    private String paymentId;

    @Column(name = "invoice_id", nullable = false)
    private String invoiceId;

    @Column(name = "idempotency_key", nullable = false, unique = true)
    private String idempotencyKey;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency_uom_id", nullable = false)
    private String currencyUomId;

    @Column(nullable = false)
    private String status;

    @Column(name = "gateway_ref")
    private String gatewayRef;

    @Column(name = "result_code")
    private String resultCode;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public PaymentEntity() {
    }

    public PaymentEntity(String paymentId, String invoiceId, String idempotencyKey,
            BigDecimal amount, String currencyUomId, String status, String gatewayRef,
            String resultCode, Instant createdAt) {
        this.paymentId = paymentId;
        this.invoiceId = invoiceId;
        this.idempotencyKey = idempotencyKey;
        this.amount = amount;
        this.currencyUomId = currencyUomId;
        this.status = status;
        this.gatewayRef = gatewayRef;
        this.resultCode = resultCode;
        this.createdAt = createdAt;
    }

    public String getPaymentId() {
        return paymentId;
    }

    public String getInvoiceId() {
        return invoiceId;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrencyUomId() {
        return currencyUomId;
    }

    public String getStatus() {
        return status;
    }

    public String getGatewayRef() {
        return gatewayRef;
    }

    public String getResultCode() {
        return resultCode;
    }
}
