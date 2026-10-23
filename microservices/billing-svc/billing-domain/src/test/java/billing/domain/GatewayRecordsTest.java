package billing.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/**
 * P5-F1-S2 gateway records: the attempt log and result shapes carried by
 * every adapter call — blank keys would break idempotency, so they cannot
 * be constructed.
 */
class GatewayRecordsTest {

    @Test
    void attemptCarriesOperationOutcomeAndKey() {
        GatewayAttempt attempt = new GatewayAttempt("PAY-1", "authorize", "idem-1", true,
            "ref-1", "APPROVED", Instant.EPOCH);
        assertEquals("PAY-1", attempt.paymentId());
        assertEquals("authorize", attempt.operation());
        assertEquals("idem-1", attempt.idempotencyKey());
        assertTrue(attempt.approved());
        assertEquals("ref-1", attempt.refNum());
        assertEquals("APPROVED", attempt.resultCode());
        assertEquals(Instant.EPOCH, attempt.attemptedAt());
    }

    @Test
    void blankKeysAndOperationsRejected() {
        assertThrows(IllegalArgumentException.class,
            () -> new GatewayAttempt("", "authorize", "k", true, null, "OK", Instant.EPOCH));
        assertThrows(IllegalArgumentException.class,
            () -> new GatewayAttempt("PAY-1", " ", "k", true, null, "OK", Instant.EPOCH));
        assertThrows(IllegalArgumentException.class,
            () -> new GatewayAttempt("PAY-1", "authorize", null, true, null, "OK",
                Instant.EPOCH));
    }

    @Test
    void resultFactories() {
        GatewayResult ok = GatewayResult.approved("ref");
        assertTrue(ok.approved());
        assertEquals("ref", ok.refNum());
        assertEquals("APPROVED", ok.resultCode());
        GatewayResult declined = GatewayResult.declined("DECLINED");
        assertFalse(declined.approved());
        assertEquals("DECLINED", declined.resultCode());
        assertEquals(BigDecimal.ZERO.signum(), 0);
    }
}
