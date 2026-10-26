package manufacturing.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import manufacturing.domain.Bom;
import manufacturing.domain.MrpEvent;
import manufacturing.domain.MrpEventType;
import manufacturing.domain.MrpProposal;
import manufacturing.domain.Routing;
import org.junit.jupiter.api.Test;

/**
 * P6-T1 use-case suite: BOM/routing CRUD with their outbox events, the
 * MRP ledger append, and the deterministic run whose proposals are the
 * CG-6 comparison against monolith MrpServices.
 */
class ManufacturingApplicationSuiteTest {

    private final FakeBomStore boms = new FakeBomStore();
    private final FakeRoutingStore routings = new FakeRoutingStore();
    private final FakeMrpEventStore events = new FakeMrpEventStore();
    private final FakeProposalStore proposals = new FakeProposalStore();
    private final List<String> outbox = new ArrayList<>();

    private final ManufacturingSerializer serializer = new ManufacturingSerializer() {
        @Override
        public String serializeBom(Bom bom) {
            return "bom:" + bom.productId();
        }

        @Override
        public String serializeBomDeleted(String productId) {
            return "bom-deleted:" + productId;
        }

        @Override
        public String serializeRouting(Routing routing) {
            return "routing:" + routing.routingId();
        }

        @Override
        public String serializeMrpProposed(MrpProposal proposal) {
            return "mrp:" + proposal.productId();
        }
    };

    private final BomService bomService = new BomService(boms,
        (t, k, p) -> outbox.add(t + ":" + k), serializer);
    private final RoutingService routingService = new RoutingService(routings,
        (t, k, p) -> outbox.add(t + ":" + k), serializer);
    private final MrpService mrp = new MrpService(events, proposals,
        (t, k, p) -> outbox.add(t + ":" + k), serializer);

    private static List<Bom.Component> components(String qty) {
        return List.of(new Bom.Component("COMP-1", new BigDecimal(qty), BigDecimal.ZERO));
    }

    private static List<Routing.Task> tasks() {
        return List.of(new Routing.Task(1, "ASSEMBLE", 30));
    }

    @Test
    void bomLifecycleEmitsCreatedUpdatedDeleted() {
        Bom created = bomService.create("P-A", components("2"));
        assertEquals("P-A", created.productId());
        assertTrue(outbox.contains("manufacturing.bom.created.v1:P-A"));
        assertThrows(IllegalStateException.class,
            () -> bomService.create("P-A", components("3")), "duplicate create conflicts");
        bomService.update("P-A", components("3"));
        assertTrue(outbox.contains("manufacturing.bom.updated.v1:P-A"));
        assertEquals("3", boms.find("P-A").orElseThrow().components().get(0).quantity()
            .toPlainString());
        assertThrows(java.util.NoSuchElementException.class,
            () -> bomService.update("P-NONE", components("1")));
        assertEquals(Optional.empty(), bomService.get("P-NONE"));
        bomService.delete("P-A");
        assertTrue(outbox.contains("manufacturing.bom.deleted.v1:P-A"));
        assertTrue(boms.find("P-A").isEmpty());
        assertThrows(java.util.NoSuchElementException.class, () -> bomService.delete("P-A"));
    }

    @Test
    void routingLifecycleEmitsCreatedUpdated() {
        Routing created = routingService.create("RT-1", "P-A", tasks());
        assertEquals("RT-1", created.routingId());
        assertTrue(outbox.contains("manufacturing.routing.created.v1:RT-1"));
        assertThrows(IllegalStateException.class,
            () -> routingService.create("RT-1", "P-A", tasks()));
        routingService.update("RT-1", "P-B", tasks());
        assertTrue(outbox.contains("manufacturing.routing.updated.v1:RT-1"));
        assertEquals("P-B", routings.find("RT-1").orElseThrow().productId());
        assertThrows(java.util.NoSuchElementException.class,
            () -> routingService.update("RT-9", "P-A", tasks()));
        assertEquals(Optional.empty(), routingService.get("RT-9"));
    }

    @Test
    void mrpRunNetsLedgerAndEmitsProposals() {
        mrp.recordEvent(new MrpEvent("s-1", "P-A", MrpEventType.SUPPLY,
            new BigDecimal("12"), Instant.parse("2026-01-01T00:00:00Z")));
        mrp.recordEvent(new MrpEvent("d-1", "P-A", MrpEventType.DEMAND,
            new BigDecimal("15"), Instant.parse("2026-01-02T00:00:00Z")));
        mrp.recordEvent(new MrpEvent("d-2", "P-B", MrpEventType.DEMAND,
            new BigDecimal("4"), Instant.parse("2026-01-02T00:00:00Z")));
        mrp.recordEvent(new MrpEvent("s-2", "P-B", MrpEventType.SUPPLY,
            new BigDecimal("10"), Instant.parse("2026-01-01T00:00:00Z")));
        assertEquals(4, mrp.events().size());

        List<MrpProposal> run = mrp.run();
        assertEquals(1, run.size());
        assertEquals("P-A", run.get(0).productId());
        assertEquals(0, run.get(0).quantity().compareTo(new BigDecimal("3")));
        assertTrue(outbox.contains("manufacturing.mrp.proposed.v1:P-A"));
        assertEquals(run, mrp.proposals(), "run persists the proposal set");
    }

    @Test
    void mrpRunReplacesPreviousProposals() {
        mrp.recordEvent(new MrpEvent("d-1", "P-A", MrpEventType.DEMAND, BigDecimal.TEN,
            Instant.parse("2026-01-01T00:00:00Z")));
        assertEquals(1, mrp.run().size());
        events.rows.clear();
        assertTrue(mrp.run().isEmpty(), "no shortage after the demand is gone");
        assertTrue(mrp.proposals().isEmpty());
        assertEquals(1, outbox.stream()
            .filter(e -> e.startsWith("manufacturing.mrp.proposed.v1")).count(),
            "the second run emits nothing new");
    }

    static final class FakeBomStore implements BomStore {
        final List<Bom> rows = new ArrayList<>();

        @Override
        public Optional<Bom> find(String productId) {
            return rows.stream().filter(b -> b.productId().equals(productId)).findFirst();
        }

        @Override
        public void save(Bom bom) {
            rows.removeIf(b -> b.productId().equals(bom.productId()));
            rows.add(bom);
        }

        @Override
        public void delete(String productId) {
            rows.removeIf(b -> b.productId().equals(productId));
        }
    }

    static final class FakeRoutingStore implements RoutingStore {
        final List<Routing> rows = new ArrayList<>();

        @Override
        public Optional<Routing> find(String routingId) {
            return rows.stream().filter(r -> r.routingId().equals(routingId)).findFirst();
        }

        @Override
        public java.util.List<Routing> all() {
            return List.copyOf(rows);
        }

        @Override
        public void save(Routing routing) {
            rows.removeIf(r -> r.routingId().equals(routing.routingId()));
            rows.add(routing);
        }
    }

    static final class FakeMrpEventStore implements MrpEventStore {
        final List<MrpEvent> rows = new ArrayList<>();

        @Override
        public void save(MrpEvent event) {
            rows.removeIf(e -> e.eventId().equals(event.eventId()));
            rows.add(event);
        }

        @Override
        public List<MrpEvent> all() {
            return List.copyOf(rows);
        }
    }

    static final class FakeProposalStore implements MrpProposalStore {
        final List<MrpProposal> rows = new ArrayList<>();

        @Override
        public void replaceAll(List<MrpProposal> proposals) {
            rows.clear();
            rows.addAll(proposals);
        }

        @Override
        public List<MrpProposal> all() {
            return List.copyOf(rows);
        }
    }
}
