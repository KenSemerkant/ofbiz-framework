package ofbiz.projection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** P2-F2-S2 AC: idempotent replay — same event id never applies twice. */
class IdempotentProjectorTest {

    private final Set<String> processed = new HashSet<>();
    private int applied;

    private final IdempotentProjector projector = new IdempotentProjector(
        new IdempotentProjector.Sink() {
            @Override
            public boolean alreadyProcessed(String eventId) {
                return processed.contains(eventId);
            }

            @Override
            public void apply(String eventId, String payload) {
                processed.add(eventId);
                applied++;
            }
        });

    @Test
    void appliesOnceAndSkipsReplays() {
        assertTrue(projector.project("ofbiz.PARTY:0:42", "{}"));
        assertFalse(projector.project("ofbiz.PARTY:0:42", "{}")); // redelivery
        assertEquals(1, applied);
        assertTrue(projector.project("ofbiz.PARTY:0:43", "{}"));
        assertEquals(2, applied);
    }

    @Test
    void blankEventIdRejected() {
        assertThrows(IllegalArgumentException.class, () -> projector.project(" ", "{}"));
    }
}
