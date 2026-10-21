package party.domain;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class PartyRelationshipTest {

    private static final Instant FROM = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant THRU = Instant.parse("2026-06-01T00:00:00Z");

    @Test
    void openEndedRelationshipAlwaysActiveAfterFrom() {
        PartyRelationship rel = new PartyRelationship("P2", "EMPLOYMENT", FROM, null);
        assertTrue(rel.activeAt(FROM));
        assertTrue(rel.activeAt(FROM.plusSeconds(60)));
    }

    @Test
    void closedRelationshipInactiveAtOrAfterThru() {
        PartyRelationship rel = new PartyRelationship("P2", "EMPLOYMENT", FROM, THRU);
        assertTrue(rel.activeAt(THRU.minusSeconds(1)));
        assertFalse(rel.activeAt(THRU));
        assertFalse(rel.activeAt(FROM.minusSeconds(1)));
    }

    @Test
    void invalidArgumentsRejected() {
        assertThrows(IllegalArgumentException.class,
            () -> new PartyRelationship(" ", "X", FROM, null));
        assertThrows(IllegalArgumentException.class,
            () -> new PartyRelationship("P2", "", FROM, null));
        assertThrows(IllegalArgumentException.class,
            () -> new PartyRelationship("P2", "X", null, null));
        assertThrows(IllegalArgumentException.class,
            () -> new PartyRelationship("P2", "X", FROM, FROM));
    }
}
