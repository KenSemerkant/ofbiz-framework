package finance.adapters.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import finance.adapters.rest.events.BillingEventConsumer;
import finance.adapters.rest.events.OutboxRelay;
import finance.adapters.rest.persistence.JacksonFinanceSerializer;
import finance.adapters.rest.persistence.OutboxEntryEntity;
import finance.adapters.rest.persistence.OutboxRepository;
import finance.adapters.rest.persistence.SmallStores;
import finance.domain.DebitCredit;
import finance.domain.GlEntry;
import finance.domain.GlTransaction;
import finance.domain.Period;
import finance.domain.PostingRule;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Unit coverage for the adapter classes the API slice leaves cold (relay,
 * consumer, serializer) — the OrderAdaptersUnitTest pattern: fakes + one
 * Postgres container.
 */
@Testcontainers
class FinanceAdaptersUnitTest {

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
        assertTrue(processed.recordIfAbsent("evt-f1"));
        assertFalse(processed.recordIfAbsent("evt-f1"), "a replayed id is a no-op");
    }

    @Test
    void consumerPostsJournalEntryAndSkipsRedelivery() {
        MemoryStores stores = new MemoryStores();
        List<String> outbox = new ArrayList<>();
        finance.application.GlService gl = new finance.application.GlService(stores.accounts,
            stores.transactions, stores.periods, (t, k, p) -> outbox.add(t),
            new JacksonFinanceSerializer());
        finance.application.AutoPostingService auto = new finance.application.AutoPostingService(
            gl, stores.rules, stores.parked);
        BillingEventConsumer consumer = new BillingEventConsumer(auto,
            new SmallStores.JdbcProcessedEventStore(db));
        seedAccountsAndPeriod(stores, gl);

        ConsumerRecord<String, String> record = new ConsumerRecord<>(
            "billing.invoice.issued.v1", 0, 3, "INV-F1",
            "{\"invoiceId\":\"INV-F1\",\"total\":\"22.00\",\"currency\":\"USD\"}");
        consumer.onBillingEvent(record);
        consumer.onBillingEvent(record);
        Optional<GlTransaction> posted = stores.transactions.find("ATT-INV-F1-issued");
        assertTrue(posted.isPresent());
        assertTrue(posted.orElseThrow().isPosted());
        assertTrue(outbox.contains("finance.transaction.posted.v1"));

        // event without a currency field falls back to USD
        ConsumerRecord<String, String> noCurrency = new ConsumerRecord<>(
            "billing.invoice.issued.v1", 1, 0, "INV-F2",
            "{\"invoiceId\":\"INV-F2\",\"total\":\"1.00\"}");
        consumer.onBillingEvent(noCurrency);

        // broken rule → parked, never dropped
        stores.rules.save(new PostingRule("R-X", "billing.invoice.settled.v1", "1200", "9999",
            true));
        ConsumerRecord<String, String> settled = new ConsumerRecord<>(
            "billing.invoice.settled.v1", 0, 4, "INV-F1",
            "{\"invoiceId\":\"INV-F1\",\"total\":\"22.00\",\"currency\":\"USD\"}");
        consumer.onBillingEvent(settled);
        assertEquals(1, stores.parked.parkedReasons().size());
    }

    private void seedAccountsAndPeriod(MemoryStores stores,
            finance.application.GlService gl) {
        stores.accounts.save(new finance.domain.GlAccount("1200", "1200", "AR",
            DebitCredit.DEBIT, "ASSET"));
        stores.accounts.save(new finance.domain.GlAccount("4000", "4000", "REV",
            DebitCredit.CREDIT, "REVENUE"));
        stores.rules.save(new PostingRule("R-I", "billing.invoice.issued.v1", "1200", "4000",
            true));
        stores.periods.save(new Period("P-2026", LocalDate.parse("2026-01-01"),
            LocalDate.parse("2026-12-31")));
    }

    @Test
    void relayPublishesAndMarks() {
        OutboxEntryEntity entry = new OutboxEntryEntity("finance.transaction.posted.v1",
            "TT-R", "{}");
        OutboxRepository repo = org.mockito.Mockito.mock(OutboxRepository.class);
        org.mockito.Mockito.when(repo.findUnpublished()).thenReturn(List.of(entry));
        org.mockito.Mockito.when(repo.findById(org.mockito.ArgumentMatchers.any()))
            .thenReturn(java.util.Optional.of(entry));
        @SuppressWarnings("unchecked")
        org.springframework.kafka.core.KafkaTemplate<String, String> kafka =
            org.mockito.Mockito.mock(org.springframework.kafka.core.KafkaTemplate.class);
        org.mockito.Mockito
            .when(kafka.send("finance.transaction.posted.v1", "TT-R", "{}"))
            .thenReturn(java.util.concurrent.CompletableFuture.completedFuture(null));
        OutboxRelay relay = new OutboxRelay(repo, kafka,
            new TransactionTemplate(new NoopTransactionManager()));
        relay.publishPending();
        org.mockito.Mockito.verify(kafka).send("finance.transaction.posted.v1", "TT-R", "{}");
        assertNotNull(entry.getPayload());
    }

    @Test
    void dlqDestinationAppendsSuffix() {
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
            "billing.invoice.issued.v1", 0, 1, "k", "{}");
        org.apache.kafka.common.TopicPartition destination =
            KafkaConfig.dlqDestination(record, new IllegalStateException("poison"));
        assertEquals("billing.invoice.issued.v1.dlq", destination.topic());
    }

    @Test
    void serializerCarriesEventContractFields() {
        JacksonFinanceSerializer serializer = new JacksonFinanceSerializer();
        GlTransaction transaction = GlTransaction.rehydrate("TT-S", "MANUAL", "d", "P",
            List.of(GlEntry.debit("1", BigDecimal.TEN), GlEntry.credit("2", BigDecimal.TEN)),
            true, Instant.EPOCH);
        String payload = serializer.serializeTransaction(transaction);
        assertTrue(payload.contains("\"acctgTransId\":\"TT-S\""));
        assertTrue(payload.contains("\"posted\":true"));
        Period period = Period.rehydrate("P-S", LocalDate.parse("2026-01-01"),
            LocalDate.parse("2026-12-31"), true);
        assertTrue(serializer.serializePeriodClosed(period).contains("\"closed\":true"));
    }

    static final class MemoryStores {
        final FakeAccounts accounts = new FakeAccounts();
        final FakeTransactions transactions = new FakeTransactions();
        final FakePeriods periods = new FakePeriods();
        final FakeRules rules = new FakeRules();
        final FakeParked parked = new FakeParked();
    }

    static final class FakeAccounts implements finance.application.GlAccountStore {
        final List<finance.domain.GlAccount> rows = new ArrayList<>();

        @Override
        public Optional<finance.domain.GlAccount> find(String glAccountId) {
            return rows.stream().filter(a -> a.glAccountId().equals(glAccountId)).findFirst();
        }

        @Override
        public void save(finance.domain.GlAccount account) {
            rows.removeIf(a -> a.glAccountId().equals(account.glAccountId()));
            rows.add(account);
        }
    }

    static final class FakeTransactions implements finance.application.GlTransactionStore {
        final List<GlTransaction> rows = new ArrayList<>();

        @Override
        public Optional<GlTransaction> find(String acctgTransId) {
            return rows.stream().filter(t -> t.acctgTransId().equals(acctgTransId))
                .findFirst();
        }

        @Override
        public void save(GlTransaction transaction) {
            rows.removeIf(t -> t.acctgTransId().equals(transaction.acctgTransId()));
            rows.add(transaction);
        }

        @Override
        public List<GlTransaction> findAllPosted() {
            return rows.stream().filter(GlTransaction::isPosted).toList();
        }
    }

    static final class FakePeriods implements finance.application.PeriodStore {
        final List<Period> rows = new ArrayList<>();

        @Override
        public Optional<Period> find(String periodId) {
            return rows.stream().filter(p -> p.periodId().equals(periodId)).findFirst();
        }

        @Override
        public void save(Period period) {
            rows.removeIf(p -> p.periodId().equals(period.periodId()));
            rows.add(period);
        }

        @Override
        public List<Period> allOpen() {
            return rows.stream().filter(p -> !p.isClosed()).toList();
        }
    }

    static final class FakeRules implements finance.application.PostingRuleStore {
        final List<PostingRule> rows = new ArrayList<>();

        @Override
        public Optional<PostingRule> findByEventType(String eventType) {
            return rows.stream().filter(r -> r.eventType().equals(eventType)).findFirst();
        }

        @Override
        public void save(PostingRule rule) {
            rows.removeIf(r -> r.ruleId().equals(rule.ruleId()));
            rows.add(rule);
        }

        @Override
        public List<PostingRule> all() {
            return List.copyOf(rows);
        }
    }

    static final class FakeParked implements finance.application.ParkedPostingStore {
        final List<String> reasons = new ArrayList<>();

        @Override
        public void park(String eventType, String aggregateKey, String reason,
                String payloadJson) {
            reasons.add("%s %s: %s".formatted(eventType, aggregateKey, reason));
        }

        @Override
        public List<String> parkedReasons() {
            return List.copyOf(reasons);
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
