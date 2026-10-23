package billing.domain;

import java.time.Instant;

/**
 * One payment-gateway adapter call (P5-F1-S2): operation + outcome + the
 * idempotency key it carried. The append-only log is the reconciliation
 * source when the gateway or the DLQ dropped work.
 */
public record GatewayAttempt(String paymentId, String operation, String idempotencyKey,
        boolean approved, String refNum, String resultCode, Instant attemptedAt) {

    public GatewayAttempt {
        if (paymentId == null || paymentId.isBlank()) {
            throw new IllegalArgumentException("paymentId must not be blank");
        }
        if (operation == null || operation.isBlank()) {
            throw new IllegalArgumentException("operation must not be blank");
        }
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("idempotencyKey must not be blank");
        }
    }
}
