package inventory.application;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import ofbiz.projection.IdempotentProjector;

/** P3-GATE: the CDC projection service skips replays (event id idempotent). */
class MonolithProjectionApplicationTest {

    @Test
    void projectionAppliesOnceAndSkipsReplays() {
        var applied = new StringBuilder();
        var service = new MonolithProjectionService(new IdempotentProjector.Sink() {
            @Override
            public boolean alreadyProcessed(String eventId) {
                return applied.indexOf(eventId) >= 0;
            }

            @Override
            public void apply(String eventId, String payload) {
                applied.append(eventId);
            }
        });
        assertTrue(service.apply("ofbiz.SHIPMENT:0:1", "{}"));
        assertFalse(service.apply("ofbiz.SHIPMENT:0:1", "{}"));
    }
}
