package party.application;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
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
}
