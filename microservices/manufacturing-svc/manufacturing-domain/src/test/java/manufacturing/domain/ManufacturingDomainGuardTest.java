package manufacturing.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * P6-T1 guard tests: aggregate construction invariants and the
 * deterministic netting — MRP over the same demand/supply dataset must
 * produce the same proposals every run (CG-6 compares against monolith
 * MrpServices).
 */
class ManufacturingDomainGuardTest {

    private static MrpEvent supply(String eventId, String product, String qty, String at) {
        return new MrpEvent(eventId, product, MrpEventType.SUPPLY,
            new BigDecimal(qty), Instant.parse(at));
    }

    private static MrpEvent demand(String eventId, String product, String qty, String at) {
        return new MrpEvent(eventId, product, MrpEventType.DEMAND,
            new BigDecimal(qty), Instant.parse(at));
    }

    @Test
    void nettingProposesShortagePerProduct() {
        List<MrpProposal> proposals = MrpPlanner.net(List.of(
            demand("d-1", "P-A", "10", "2026-01-02T00:00:00Z"),
            demand("d-2", "P-A", "5", "2026-01-03T00:00:00Z"),
            supply("s-1", "P-A", "12", "2026-01-01T00:00:00Z"),
            supply("s-2", "P-B", "40", "2026-01-01T00:00:00Z"),
            demand("d-3", "P-B", "10", "2026-01-02T00:00:00Z")));
        assertEquals(1, proposals.size());
        assertEquals("P-A", proposals.get(0).productId());
        assertEquals(0, proposals.get(0).quantity().compareTo(new BigDecimal("3")));
    }

    @Test
    void nettingIsDeterministicAcrossShuffledInput() {
        List<MrpEvent> events = List.of(
            supply("s-1", "P-A", "2", "2026-01-01T00:00:00Z"),
            demand("d-2", "P-A", "10", "2026-01-03T00:00:00Z"),
            demand("d-1", "P-A", "10", "2026-01-02T00:00:00Z"));
        List<MrpProposal> first = MrpPlanner.net(events);
        List<MrpProposal> second = MrpPlanner.net(events.reversed());
        assertEquals(first, second, "event arrival order never changes the result");
        assertEquals(0, first.get(0).quantity().compareTo(new BigDecimal("18")));
    }

    @Test
    void balancedOrSurplusProductsGetNoProposal() {
        List<MrpProposal> proposals = MrpPlanner.net(List.of(
            supply("s-1", "P-EQ", "10", "2026-01-01T00:00:00Z"),
            demand("d-1", "P-EQ", "10", "2026-01-02T00:00:00Z"),
            supply("s-2", "P-SUR", "10", "2026-01-01T00:00:00Z"),
            demand("d-2", "P-SUR", "2", "2026-01-02T00:00:00Z")));
        assertTrue(proposals.isEmpty());
        assertTrue(MrpPlanner.net(List.of()).isEmpty());
    }

    @Test
    void bomAndRoutingGuards() {
        Bom.Component c = new Bom.Component("COMP-1", new BigDecimal("2"),
            new BigDecimal("5"));
        Bom bom = new Bom("P-A", List.of(c));
        assertEquals("P-A", bom.productId());
        assertEquals(1, bom.components().size());
        assertThrows(IllegalArgumentException.class, () -> new Bom(" ", List.of(c)));
        assertThrows(IllegalArgumentException.class, () -> new Bom("P-A", List.of()));
        assertThrows(IllegalArgumentException.class,
            () -> new Bom.Component(" ", BigDecimal.ONE, BigDecimal.ZERO));
        assertThrows(IllegalArgumentException.class,
            () -> new Bom.Component("COMP", BigDecimal.ZERO, BigDecimal.ZERO));
        assertThrows(IllegalArgumentException.class,
            () -> new Bom.Component("COMP", BigDecimal.ONE, new BigDecimal("101")));

        Routing.Task t = new Routing.Task(1, "ASSEMBLE", 30);
        Routing routing = new Routing("RT-1", "P-A", List.of(t));
        assertEquals("RT-1", routing.routingId());
        assertEquals("P-A", routing.productId());
        assertEquals(1, routing.tasks().size());
        assertThrows(IllegalArgumentException.class, () -> new Routing(" ", "P", List.of(t)));
        assertThrows(IllegalArgumentException.class, () -> new Routing("R", " ", List.of(t)));
        assertThrows(IllegalArgumentException.class, () -> new Routing("R", "P", List.of()));
        assertThrows(IllegalArgumentException.class,
            () -> new Routing.Task(0, "T", 30));
        assertThrows(IllegalArgumentException.class,
            () -> new Routing.Task(1, " ", 30));
        assertThrows(IllegalArgumentException.class,
            () -> new Routing.Task(1, "T", 0));
    }

    @Test
    void mrpEventAndProposalGuards() {
        MrpEvent event = demand("e-1", "P-A", "10", "2026-01-01T00:00:00Z");
        assertEquals("P-A", event.productId());
        assertEquals(MrpEventType.DEMAND, event.eventType());
        assertThrows(IllegalArgumentException.class,
            () -> demand(" ", "P", "1", "2026-01-01T00:00:00Z"));
        assertThrows(IllegalArgumentException.class,
            () -> demand("e", " ", "1", "2026-01-01T00:00:00Z"));
        assertThrows(IllegalArgumentException.class,
            () -> demand("e", "P", "0", "2026-01-01T00:00:00Z"));
        assertThrows(IllegalArgumentException.class,
            () -> new MrpEvent("e", "P", MrpEventType.SUPPLY, BigDecimal.ONE, null));
        assertThrows(IllegalArgumentException.class,
            () -> new MrpEvent("e", "P", null, BigDecimal.ONE,
                Instant.parse("2026-01-01T00:00:00Z")));
        MrpProposal proposal = new MrpProposal("P-A", BigDecimal.TEN);
        assertEquals("P-A", proposal.productId());
        assertThrows(IllegalArgumentException.class, () -> new MrpProposal(" ", BigDecimal.TEN));
        assertThrows(IllegalArgumentException.class,
            () -> new MrpProposal("P-A", BigDecimal.ZERO));
    }
}
