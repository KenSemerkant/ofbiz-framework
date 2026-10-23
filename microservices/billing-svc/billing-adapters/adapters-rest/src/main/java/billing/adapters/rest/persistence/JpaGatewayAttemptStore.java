package billing.adapters.rest.persistence;

import java.util.List;
import org.springframework.stereotype.Repository;
import billing.application.GatewayAttemptStore;
import billing.domain.GatewayAttempt;

/** JPA adapter for the GatewayAttemptStore port (append-only). */
@Repository
public class JpaGatewayAttemptStore implements GatewayAttemptStore {

    private final GatewayAttemptRepository attempts;

    public JpaGatewayAttemptStore(GatewayAttemptRepository attempts) {
        this.attempts = attempts;
    }

    @Override
    public void append(GatewayAttempt attempt) {
        attempts.save(new GatewayAttemptEntity(attempt.paymentId(), attempt.operation(),
            attempt.idempotencyKey(), attempt.approved(), attempt.refNum(),
            attempt.resultCode()));
    }

    @Override
    public List<GatewayAttempt> all() {
        return attempts.findAll().stream()
            .map(e -> new GatewayAttempt(e.getPaymentId(), e.getOperation(),
                e.getIdempotencyKey(), e.isApproved(), e.getRefNum(), e.getResultCode(),
                e.getAttemptedAt()))
            .toList();
    }
}
