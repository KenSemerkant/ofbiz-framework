package billing.adapters.rest.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** gateway_attempts row (P5-F1-S2): append-only adapter-call log. */
@Entity
@Table(name = "gateway_attempts")
public class GatewayAttemptEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "payment_id", nullable = false)
    private String paymentId;

    @Column(nullable = false)
    private String operation;

    @Column(name = "idempotency_key", nullable = false)
    private String idempotencyKey;

    @Column(nullable = false)
    private boolean approved;

    @Column(name = "ref_num")
    private String refNum;

    @Column(name = "result_code", nullable = false)
    private String resultCode;

    @Column(name = "attempted_at", nullable = false)
    private Instant attemptedAt = Instant.now();

    public GatewayAttemptEntity() {
    }

    public GatewayAttemptEntity(String paymentId, String operation, String idempotencyKey,
            boolean approved, String refNum, String resultCode) {
        this.paymentId = paymentId;
        this.operation = operation;
        this.idempotencyKey = idempotencyKey;
        this.approved = approved;
        this.refNum = refNum;
        this.resultCode = resultCode;
    }

    public String getPaymentId() {
        return paymentId;
    }

    public String getOperation() {
        return operation;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public boolean isApproved() {
        return approved;
    }

    public String getRefNum() {
        return refNum;
    }

    public String getResultCode() {
        return resultCode;
    }

    public Instant getAttemptedAt() {
        return attemptedAt;
    }
}
