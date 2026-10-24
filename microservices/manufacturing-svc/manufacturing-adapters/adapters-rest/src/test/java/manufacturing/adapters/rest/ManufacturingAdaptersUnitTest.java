package manufacturing.adapters.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import manufacturing.adapters.rest.events.InventorySupplyConsumer;
import manufacturing.adapters.rest.events.OrderDemandConsumer;
import manufacturing.adapters.rest.events.OutboxRelay;
import manufacturing.adapters.rest.persistence.JacksonManufacturingSerializer;
import manufacturing.adapters.rest.persistence.OutboxEntryEntity;
import manufacturing.adapters.rest.persistence.OutboxRepository;
import manufacturing.adapters.rest.persistence.SmallStores;
import manufacturing.application.MrpEventStore;
import manufacturing.application.MrpProposalStore;
import manufacturing.domain.Bom;
import manufacturing.domain.MrpEvent;
import manufacturing.domain.MrpEventType;
import manufacturing.domain.MrpProposal;
import manufacturing.domain.Routing;

/**
 * Unit coverage for the adapter classes the API slice leaves cold (relay,
 * consumers, serializer) — the FinanceAdaptersUnitTest pattern: fakes +
 * one Postgres container.
 */
@Testcontainers
class ManufacturingAdaptersUnitTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    static JdbcTemplate db;

    @BeforeAll
    static void schema() {
        db = new JdbcTemplate(new DriverManagerDataSource(POSTGRES.getJdbcUrl(),
            POSTGRES.getUsername(), POSTGRES.getPassword()));
        db.execute("create table processed_events (event_id varchar(255) primary key,"
            + " processed_at timestamptz not null default now())");
    }

    @Test
    void processedEventStoreIsIdempotent() {
        SmallStores.JdbcProcessedEventStore processed = new SmallStores.JdbcProcessedEventStore(
            db);
        assertTrue(processed.recordIfAbsent("evt-m1"));
        assertFalse(processed.recordIfAbsent("evt-m1"), "a replayed id is a no-op");
    }

    @Test
    void demandConsumerAppendsOneLedgerRowPerOrderLine() {
        FakeEventStore events = new FakeEventStore();
        OrderDemandConsumer consumer = new OrderDemandConsumer(newMrp(events),
            new SmallStores.JdbcProcessedEventStore(db));
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
            "order.order.placed.v1", 0, 3, "ORD-1", """
                {"orderId":"ORD-1","placedAt":"2026-01-02T00:00:00Z",
                 "lines":[{"orderItemSeqId":"1","productId":"P-A","quantity":10},
                          {"orderItemSeqId":"2","productId":"P-B","quantity":4}]}
                """);
        consumer.onOrderPlaced(record);
        consumer.onOrderPlaced(record);
        assertEquals(2, events.rows.size(), "a redelivered order appends nothing");
        MrpEvent first = events.rows.get(0);
        assertEquals("P-A", first.productId());
        assertEquals(MrpEventType.DEMAND, first.eventType());
        assertEquals(0, first.quantity().compareTo(BigDecimal.TEN));
        assertEquals("order.order.placed.v1:0:3:0", first.eventId());
        assertEquals(Instant.parse("2026-01-02T00:00:00Z"), first.occurredAt());
        assertEquals(MrpEventType.DEMAND, events.rows.get(1).eventType());
        assertEquals("P-B", events.rows.get(1).productId());
    }

    @Test
    void demandConsumerFallsBackToRecordTimestampWithoutPlacedAt() {
        FakeEventStore events = new FakeEventStore();
        OrderDemandConsumer consumer = new OrderDemandConsumer(newMrp(events),
            new SmallStores.JdbcProcessedEventStore(db));
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
            "order.order.placed.v1", 1, 0, 1767225600000L, // 2026-01-01T00:00:00Z
            org.apache.kafka.common.record.TimestampType.CREATE_TIME, -1, -1, "ORD-2",
            "{\"orderId\":\"ORD-2\",\"lines\":[{\"productId\":\"P-A\",\"quantity\":2}]}",
            new org.apache.kafka.common.header.internals.RecordHeaders(),
            java.util.Optional.empty());
        consumer.onOrderPlaced(record);
        assertEquals(1, events.rows.size());
        assertEquals(Instant.ofEpochMilli(1767225600000L),
            events.rows.get(0).occurredAt());
    }

    @Test
    void supplyConsumerNetsReceivedAndAdjusted() {
        FakeEventStore events = new FakeEventStore();
        InventorySupplyConsumer consumer = new InventorySupplyConsumer(newMrp(events),
            new SmallStores.JdbcProcessedEventStore(db));
        consumer.onStockEvent(new ConsumerRecord<>("inventory.stock.received.v1", 0, 1,
            "II-1", "{\"productId\":\"P-A\",\"quantity\":12}"));
        consumer.onStockEvent(new ConsumerRecord<>("inventory.stock.adjusted.v1", 0, 2,
            "II-1", "{\"productId\":\"P-A\",\"quantity\":-2}"));
        assertEquals(2, events.rows.size());
        assertTrue(events.rows.stream()
            .allMatch(e -> e.eventType() == MrpEventType.SUPPLY));
        assertEquals(0, events.rows.get(1).quantity().compareTo(new BigDecimal("-2")));
    }

    @Test
    void relayPublishesAndMarks() {
        OutboxEntryEntity entry = new OutboxEntryEntity("manufacturing.mrp.proposed.v1",
            "P-A", "{}");
        OutboxRepository repo = org.mockito.Mockito.mock(OutboxRepository.class);
        org.mockito.Mockito.when(repo.findUnpublished()).thenReturn(List.of(entry));
        org.mockito.Mockito.when(repo.findById(org.mockito.ArgumentMatchers.any()))
            .thenReturn(java.util.Optional.of(entry));
        @SuppressWarnings("unchecked")
        org.springframework.kafka.core.KafkaTemplate<String, String> kafka =
            org.mockito.Mockito.mock(org.springframework.kafka.core.KafkaTemplate.class);
        org.mockito.Mockito
            .when(kafka.send("manufacturing.mrp.proposed.v1", "P-A", "{}"))
            .thenReturn(java.util.concurrent.CompletableFuture.completedFuture(null));
        OutboxRelay relay = new OutboxRelay(repo, kafka,
            new TransactionTemplate(new NoopTransactionManager()));
        relay.publishPending();
        org.mockito.Mockito.verify(kafka)
            .send("manufacturing.mrp.proposed.v1", "P-A", "{}");
        assertNotNull(entry.getPayload());
    }

    @Test
    void dlqDestinationAppendsSuffix() {
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
            "order.order.placed.v1", 0, 1, "k", "{}");
        org.apache.kafka.common.TopicPartition destination =
            KafkaConfig.dlqDestination(record, new IllegalStateException("poison"));
        assertEquals("order.order.placed.v1.dlq", destination.topic());
    }

    @Test
    void serializerCarriesEventContractFields() {
        JacksonManufacturingSerializer serializer = new JacksonManufacturingSerializer();
        Bom bom = new Bom("P-A", List.of(new Bom.Component("COMP", new BigDecimal("2"),
            new BigDecimal("5"))));
        String bomPayload = serializer.serializeBom(bom);
        assertTrue(bomPayload.contains("\"productId\":\"P-A\""));
        assertTrue(bomPayload.contains("\"components\":["));
        assertTrue(bomPayload.contains("\"scrapPct\":5"));
        assertTrue(serializer.serializeBomDeleted("P-A").contains("\"productId\":\"P-A\""));
        Routing routing = new Routing("RT-1", "P-A",
            List.of(new Routing.Task(1, "ASSEMBLE", 30)));
        String routingPayload = serializer.serializeRouting(routing);
        assertTrue(routingPayload.contains("\"routingId\":\"RT-1\""));
        assertTrue(routingPayload.contains("\"estimatedDuration\":30"));
        String mrpPayload = serializer.serializeMrpProposed(new MrpProposal("P-A",
            new BigDecimal("3.000")));
        assertTrue(mrpPayload.contains("\"productId\":\"P-A\""));
        assertTrue(mrpPayload.contains("\"quantity\":3.000"));
    }

    private manufacturing.application.MrpService newMrp(FakeEventStore events) {
        return new manufacturing.application.MrpService(events, new FakeProposalStore(),
            (type, key, payload) -> {
            }, new JacksonManufacturingSerializer());
    }

    static final class FakeEventStore implements MrpEventStore {
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

    static class NoopTransactionManager implements PlatformTransactionManager {
        @Override
        public TransactionStatus getTransaction(TransactionDefinition definition) {
            return new SimpleTransactionStatus();
        }

        @Override
        public void commit(TransactionStatus status) {
        }

        @Override
        public void rollback(TransactionStatus status) {
        }
    }
}
