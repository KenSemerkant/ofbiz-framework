package party.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import ofbiz.projection.IdempotentProjector;
import org.junit.jupiter.api.Test;

/** Unit coverage for the P2-F2 application services and the page envelope. */
class PartyApplicationServicesTest {

    @Test
    void partyPageTotals() {
        var page = new PartyPage(List.of(), 2, 10, 95);
        assertEquals(10, page.totalPages());
        assertEquals(0, new PartyPage(List.of(), 0, 0, 95).totalPages());
        var summary = new PartyPage.PartySummary("P1", "PERSON", "A B");
        assertEquals("P1", summary.partyId());
    }

    @Test
    void projectorAppliesOncePerEventId() {
        List<String> applied = new ArrayList<>();
        var sink = new IdempotentProjector.Sink() {
            @Override
            public boolean alreadyProcessed(String eventId) {
                return applied.contains(eventId);
            }

            @Override
            public void apply(String eventId, String payload) {
                applied.add(eventId);
            }
        };
        var service = new MonolithProjectionService(sink);
        assertTrue(service.apply("ofbiz.PARTY:0:1", "{}"));
        assertFalse(service.apply("ofbiz.PARTY:0:1", "{}"));
        assertThrows(IllegalArgumentException.class, () -> service.apply(" ", "{}"));
    }

    @Test
    void reverseSyncForwardsSnapshotToMonolithWriter() {
        List<Object[]> written = new ArrayList<>();
        MonolithSyncWriter writer = (partyId, kind, first, last, group, roles) ->
            written.add(new Object[] {partyId, kind, first, last, group, roles});
        var snapshot = new ReverseSyncService.PartySnapshot(
            "P1", "PERSON", "Ada", "Lovelace", null, Set.of("CUSTOMER"));
        new ReverseSyncService(writer).onPartyEvent(snapshot);
        assertEquals(1, written.size());
        assertEquals("P1", written.get(0)[0]);
        assertEquals(Set.of("CUSTOMER"), written.get(0)[5]);
    }
}
