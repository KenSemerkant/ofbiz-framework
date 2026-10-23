package billing.adapters.rest.integration;

import java.math.BigDecimal;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import billing.application.GatewayUnavailableException;
import billing.application.PaymentGateway;
import billing.domain.GatewayResult;

/**
 * P5-F1-S2 test gateway (replaces PaymentGatewayServices for staging):
 * deterministic approvals whose behavior is steerable at runtime so the
 * e2e can drive approve/decline/outage paths. Bounded retry (3 attempts)
 * rides in the adapter — a real PSP adapter would retry the same way on
 * connect failures. PCI scope: no card data crosses this boundary, only
 * the idempotency key + amount (see PCI-SCOPE.md).
 */
@Component
public class TestGatewayAdapter implements PaymentGateway {

    private final int maxAttempts;
    /** Runtime steering for drills; flips back automatically after use. */
    public final AtomicBoolean down = new AtomicBoolean();
    public final AtomicBoolean declineNext = new AtomicBoolean();

    public TestGatewayAdapter(@Value("${billing.gateway.retry-attempts:3}") int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    @Override
    public GatewayResult authorize(String idempotencyKey, BigDecimal amount,
            String currencyUomId) {
        return answer("authorize", idempotencyKey,
            "auth-" + idempotencyKey + "-" + Integer.toHexString(
                ThreadLocalRandom.current().nextInt()));
    }

    @Override
    public GatewayResult capture(String idempotencyKey, String refNum, BigDecimal amount) {
        return answer("capture", idempotencyKey, "cap-" + refNum);
    }

    @Override
    public GatewayResult voidAuthorization(String idempotencyKey, String refNum) {
        return answer("void", idempotencyKey, "void-" + refNum);
    }

    private GatewayResult answer(String operation, String idempotencyKey, String ref) {
        RuntimeException last = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            if (down.get()) {
                last = new IllegalStateException("test-gateway: connection refused");
                pause();
                continue;
            }
            if (declineNext.compareAndSet(true, false)) {
                return GatewayResult.declined("DECLINED");
            }
            return GatewayResult.approved(ref);
        }
        throw new GatewayUnavailableException(operation, maxAttempts, last);
    }

    private void pause() {
        java.util.concurrent.locks.LockSupport.parkNanos(50_000_000L);
    }
}
