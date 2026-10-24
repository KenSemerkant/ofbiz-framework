package workeffort.adapters.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
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
import workeffort.adapters.rest.events.OutboxRelay;
import workeffort.adapters.rest.persistence.JacksonWorkEffortSerializer;
import workeffort.adapters.rest.persistence.OutboxEntryEntity;
import workeffort.adapters.rest.persistence.OutboxRepository;
import workeffort.adapters.rest.persistence.SmallStores;
import workeffort.adapters.rest.persistence.TimesheetEntryJson;
import workeffort.domain.Timesheet;
import workeffort.domain.TimesheetEntry;
import workeffort.domain.TimesheetStatus;
import workeffort.domain.WorkEffort;
import workeffort.domain.WorkEffortStatus;
import workeffort.domain.WorkEffortType;

/**
 * Unit coverage for the adapter classes the API slice leaves cold (relay,
 * serializer, inline-entry JSON) — the FinanceAdaptersUnitTest pattern:
 * fakes + one Postgres container.
 */
@Testcontainers
class WorkeffortAdaptersUnitTest {

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
        assertTrue(processed.recordIfAbsent("evt-w1"));
        assertFalse(processed.recordIfAbsent("evt-w1"), "a replayed id is a no-op");
    }

    @Test
    void inlineEntryJsonRoundTrips() {
        List<TimesheetEntry> entries = List.of(
            new TimesheetEntry("WE-1", new BigDecimal("7.5"), "STANDARD"),
            new TimesheetEntry("WE-2", new BigDecimal("2.25"), "OVERTIME"));
        List<TimesheetEntry> back = TimesheetEntryJson.deserialize(
            TimesheetEntryJson.serialize(entries));
        assertEquals(entries, back);
    }

    @Test
    void relayPublishesAndMarks() {
        OutboxEntryEntity entry = new OutboxEntryEntity("workeffort.effort.created.v1",
            "WE-R", "{}");
        OutboxRepository repo = org.mockito.Mockito.mock(OutboxRepository.class);
        org.mockito.Mockito.when(repo.findUnpublished()).thenReturn(List.of(entry));
        org.mockito.Mockito.when(repo.findById(org.mockito.ArgumentMatchers.any()))
            .thenReturn(java.util.Optional.of(entry));
        @SuppressWarnings("unchecked")
        org.springframework.kafka.core.KafkaTemplate<String, String> kafka =
            org.mockito.Mockito.mock(org.springframework.kafka.core.KafkaTemplate.class);
        org.mockito.Mockito
            .when(kafka.send("workeffort.effort.created.v1", "WE-R", "{}"))
            .thenReturn(java.util.concurrent.CompletableFuture.completedFuture(null));
        OutboxRelay relay = new OutboxRelay(repo, kafka,
            new TransactionTemplate(new NoopTransactionManager()));
        relay.publishPending();
        org.mockito.Mockito.verify(kafka).send("workeffort.effort.created.v1", "WE-R", "{}");
        assertNotNull(entry.getPayload());
    }

    @Test
    void dlqDestinationAppendsSuffix() {
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
            "workeffort.effort.created.v1", 0, 1, "k", "{}");
        org.apache.kafka.common.TopicPartition destination =
            KafkaConfig.dlqDestination(record, new IllegalStateException("poison"));
        assertEquals("workeffort.effort.created.v1.dlq", destination.topic());
    }

    @Test
    void serializerCarriesEventContractFields() {
        JacksonWorkEffortSerializer serializer = new JacksonWorkEffortSerializer();
        WorkEffort effort = WorkEffort.rehydrate("WE-S", WorkEffortType.TASK, "task, one; two",
            "WE-P", WorkEffortStatus.IN_PROGRESS, LocalDate.parse("2026-01-05"),
            LocalDate.parse("2026-01-09"));
        String payload = serializer.serializeEffort(effort);
        assertTrue(payload.contains("\"workEffortId\":\"WE-S\""));
        assertTrue(payload.contains("\"statusId\":\"IN_PROGRESS\""));
        assertTrue(payload.contains("\"parentWorkEffortId\":\"WE-P\""));
        assertTrue(payload.contains("\"estimatedStartDate\":\"2026-01-05\""));
        Timesheet sheet = Timesheet.rehydrate("TS-S", "P-1", LocalDate.parse("2026-01-05"),
            LocalDate.parse("2026-01-11"), List.of(new TimesheetEntry("WE-S",
                BigDecimal.ONE, "STANDARD")), TimesheetStatus.SUBMITTED);
        String sheetPayload = serializer.serializeTimesheetSubmitted(sheet);
        assertTrue(sheetPayload.contains("\"timesheetId\":\"TS-S\""));
        assertTrue(sheetPayload.contains("\"statusId\":\"SUBMITTED\""));
        assertTrue(sheetPayload.contains("\"entryCount\":1"));
        // dateless efforts serialize without the date keys, not with nulls
        WorkEffort bare = WorkEffort.rehydrate("WE-B", WorkEffortType.EVENT, "bare", null,
            WorkEffortStatus.CREATED, null, null);
        String barePayload = serializer.serializeEffort(bare);
        assertFalse(barePayload.contains("estimatedStartDate"));
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
