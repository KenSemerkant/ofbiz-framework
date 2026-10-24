package marketing.adapters.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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
import marketing.adapters.rest.events.OrderPlacedConsumer;
import marketing.adapters.rest.events.OutboxRelay;
import marketing.adapters.rest.persistence.JacksonMarketingSerializer;
import marketing.adapters.rest.persistence.OutboxEntryEntity;
import marketing.adapters.rest.persistence.OutboxRepository;
import marketing.adapters.rest.persistence.SmallStores;
import marketing.application.AttributionStore;
import marketing.application.TrackingCodeStore;
import marketing.domain.TrackingCode;

/**
 * Unit coverage for the adapter classes the API slice leaves cold (relay,
 * consumer, serializer) — the FinanceAdaptersUnitTest pattern: fakes + one
 * Postgres container.
 */
@Testcontainers
class MarketingAdaptersUnitTest {

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
    void consumerRecordsCodedOrdersAndSkipsUncoded() {
        FakeAttributionStore attributions = new FakeAttributionStore();
        OrderPlacedConsumer consumer = new OrderPlacedConsumer(
            new marketing.application.AttributionService(attributions,
                new FakeTrackingCodeStore()),
            new SmallStores.JdbcProcessedEventStore(db));

        consumer.onOrderPlaced(new ConsumerRecord<>("order.order.placed.v1", 0, 3,
            "ORDER-A1", "{\"orderId\":\"ORDER-A1\",\"customerId\":\"CUST-1\","
                + "\"placedAt\":\"2026-02-01T10:00:00Z\",\"grandTotal\":\"22.00\","
                + "\"currency\":\"USD\",\"lineCount\":2,\"trackingCode\":\"PROMO-42\"}"));
        // redelivery: processed_events says no
        consumer.onOrderPlaced(new ConsumerRecord<>("order.order.placed.v1", 0, 3,
            "ORDER-A1", "{\"orderId\":\"ORDER-A1\",\"trackingCode\":\"PROMO-42\","
                + "\"grandTotal\":\"22.00\"}"));
        // uncoded order: skipped, never recorded
        consumer.onOrderPlaced(new ConsumerRecord<>("order.order.placed.v1", 0, 4,
            "ORDER-A2", "{\"orderId\":\"ORDER-A2\",\"grandTotal\":\"10.00\"}"));
        // unknown tracking code: still recorded (report shows null campaign)
        consumer.onOrderPlaced(new ConsumerRecord<>("order.order.placed.v1", 0, 5,
            "ORDER-A3", "{\"orderId\":\"ORDER-A3\",\"trackingCode\":\"UNKNOWN-9\","
                + "\"grandTotal\":\"5.00\"}"));

        assertEquals(2, attributions.rows.size());
        assertEquals("PROMO-42", attributions.rows.get(0).trackingCode());
        assertEquals(0, attributions.rows.get(0).orderTotal()
            .compareTo(new BigDecimal("22.00")));
        assertEquals("UNKNOWN-9", attributions.rows.get(1).trackingCode());
    }

    @Test
    void relayPublishesAndMarks() {
        OutboxEntryEntity entry = new OutboxEntryEntity("marketing.campaign.created.v1",
            "MC-R", "{}");
        OutboxRepository repo = org.mockito.Mockito.mock(OutboxRepository.class);
        org.mockito.Mockito.when(repo.findUnpublished()).thenReturn(List.of(entry));
        org.mockito.Mockito.when(repo.findById(org.mockito.ArgumentMatchers.any()))
            .thenReturn(java.util.Optional.of(entry));
        @SuppressWarnings("unchecked")
        org.springframework.kafka.core.KafkaTemplate<String, String> kafka =
            org.mockito.Mockito.mock(org.springframework.kafka.core.KafkaTemplate.class);
        org.mockito.Mockito
            .when(kafka.send("marketing.campaign.created.v1", "MC-R", "{}"))
            .thenReturn(java.util.concurrent.CompletableFuture.completedFuture(null));
        OutboxRelay relay = new OutboxRelay(repo, kafka,
            new TransactionTemplate(new NoopTransactionManager()));
        relay.publishPending();
        org.mockito.Mockito.verify(kafka).send("marketing.campaign.created.v1", "MC-R", "{}");
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
        JacksonMarketingSerializer serializer = new JacksonMarketingSerializer();
        assertTrue(serializer.serializeCampaign(new marketing.domain.MarketingCampaign("MC-S",
            "Spring", marketing.domain.CampaignStatus.ACTIVE,
            LocalDate.parse("2026-01-01"), LocalDate.parse("2026-12-31")))
            .contains("\"statusId\":\"ACTIVE\""));
        assertTrue(serializer.serializeTrackingCode(new TrackingCode("TC-S", null, null,
            LocalDate.parse("2026-01-01"), LocalDate.parse("2026-12-31")))
            .contains("\"trackingCodeId\":\"TC-S\""));
        assertTrue(serializer.serializeOpportunity(new marketing.domain.SalesOpportunity(
            "SO-S", null, "QUALIFIED", 50)).contains("\"closeProbability\":50"));
    }

    static final class FakeTrackingCodeStore implements TrackingCodeStore {
        final List<TrackingCode> rows = new ArrayList<>();

        @Override
        public Optional<TrackingCode> find(String trackingCodeId) {
            return rows.stream().filter(t -> t.trackingCodeId().equals(trackingCodeId))
                .findFirst();
        }

        @Override
        public void save(TrackingCode trackingCode) {
            rows.removeIf(t -> t.trackingCodeId().equals(trackingCode.trackingCodeId()));
            rows.add(trackingCode);
        }

        @Override
        public List<TrackingCode> all() {
            return List.copyOf(rows);
        }
    }

    /** Mimics the unique-order_id insert: duplicates are silently skipped. */
    static final class FakeAttributionStore implements AttributionStore {
        final List<Attribution> rows = new ArrayList<>();

        @Override
        public void record(Attribution attribution) {
            if (rows.stream().noneMatch(a -> a.orderId().equals(attribution.orderId()))) {
                rows.add(attribution);
            }
        }

        @Override
        public List<Attribution> findAll() {
            return List.copyOf(rows);
        }

        @Override
        public List<Attribution> findByTrackingCode(String trackingCode) {
            return rows.stream().filter(a -> a.trackingCode().equals(trackingCode)).toList();
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
