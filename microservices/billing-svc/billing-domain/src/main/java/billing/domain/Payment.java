package billing.domain;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Payment aggregate root (P5-F1-S2): one customer payment applied to one
 * invoice. Gateway ops move it through {@link PaymentStatus}; the
 * idempotencyKey is enforced unique at the store so a retried API call can
 * never double-charge.
 */
public final class Payment {

    private final String paymentId;
    private final String invoiceId;
    private final String idempotencyKey;
    private final BigDecimal amount;
    private final String currencyUomId;
    private final Instant createdAt;
    private PaymentStatus status;
    private String gatewayRef;
    private String resultCode;

    public Payment(String paymentId, String invoiceId, String idempotencyKey,
            BigDecimal amount, String currencyUomId, Instant createdAt) {
        if (paymentId == null || paymentId.isBlank()) {
            throw new IllegalArgumentException("paymentId must not be blank");
        }
        if (invoiceId == null || invoiceId.isBlank()) {
            throw new IllegalArgumentException("invoiceId must not be blank");
        }
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("idempotencyKey must not be blank");
        }
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
        if (currencyUomId == null || currencyUomId.isBlank()) {
            throw new IllegalArgumentException("currencyUomId must not be blank");
        }
        this.paymentId = paymentId;
        this.invoiceId = invoiceId;
        this.idempotencyKey = idempotencyKey;
        this.amount = amount;
        this.currencyUomId = currencyUomId;
        this.createdAt = createdAt;
        this.status = PaymentStatus.PENDING;
    }

    public void authorize(String gatewayRef, String resultCode) {
        require(PaymentStatus.AUTHORIZED);
        this.gatewayRef = gatewayRef;
        this.resultCode = resultCode;
        this.status = PaymentStatus.AUTHORIZED;
    }

    public void capture(String resultCode) {
        require(PaymentStatus.CAPTURED);
        this.resultCode = resultCode;
        this.status = PaymentStatus.CAPTURED;
    }

    public void voidAuthorization(String resultCode) {
        require(PaymentStatus.VOIDED);
        this.resultCode = resultCode;
        this.status = PaymentStatus.VOIDED;
    }

    public void fail(String resultCode) {
        require(PaymentStatus.FAILED);
        this.resultCode = resultCode;
        this.status = PaymentStatus.FAILED;
    }

    private void require(PaymentStatus to) {
        PaymentStatus.requireTransition(status, to);
    }

    public static Payment rehydrate(String paymentId, String invoiceId, String idempotencyKey,
            BigDecimal amount, String currencyUomId, PaymentStatus status, String gatewayRef,
            String resultCode) {
        Payment payment = new Payment(paymentId, invoiceId, idempotencyKey, amount,
            currencyUomId, Instant.EPOCH);
        payment.status = status;
        payment.gatewayRef = gatewayRef;
        payment.resultCode = resultCode;
        return payment;
    }

    public String paymentId() {
        return paymentId;
    }

    public String invoiceId() {
        return invoiceId;
    }

    public String idempotencyKey() {
        return idempotencyKey;
    }

    public BigDecimal amount() {
        return amount;
    }

    public String currencyUomId() {
        return currencyUomId;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public PaymentStatus status() {
        return status;
    }

    public String gatewayRef() {
        return gatewayRef;
    }

    public String resultCode() {
        return resultCode;
    }
}
