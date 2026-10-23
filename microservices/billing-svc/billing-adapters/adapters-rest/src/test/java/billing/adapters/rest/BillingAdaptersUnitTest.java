package billing.adapters.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.AfterAll;
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
import billing.adapters.rest.events.BillingConsumers;
import billing.adapters.rest.events.OutboxRelay;
import billing.adapters.rest.integration.MinimalPdfRenderer;
import billing.adapters.rest.integration.TestGatewayAdapter;
import billing.adapters.rest.persistence.JacksonBillingSerializer;
import billing.adapters.rest.persistence.OutboxEntryEntity;
import billing.adapters.rest.persistence.OutboxRepository;
import billing.adapters.rest.persistence.SmallStores;
import billing.application.BillingSerializer;
import billing.application.GatewayUnavailableException;
import billing.domain.GatewayResult;
import billing.domain.Invoice;
import billing.domain.InvoiceLine;
import billing.domain.InvoiceStatus;
import billing.domain.InvoiceStatusChange;
import billing.domain.Payment;
import billing.domain.PaymentStatus;
import java.time.Instant;

/**
 * Unit coverage for the adapter classes the API slice leaves cold (relay,
 * consumer, test gateway, PDF renderer, serializer) — the OrderAdaptersUnitTest
 * pattern: fakes + one Postgres container.
 */
@Testcontainers
class BillingAdaptersUnitTest {

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

    @AfterAll
    static void nothing() {
        // container lifecycle is handled by @Container
    }

    // ---- processed events ----

    @Test
    void processedEventStoreIsIdempotent() {
        SmallStores.JdbcProcessedEventStore processed = new SmallStores.JdbcProcessedEventStore(
            db);
        assertTrue(processed.recordIfAbsent("evt-1"));
        assertFalse(processed.recordIfAbsent("evt-1"), "a replayed id is a no-op");
    }

    // ---- invoice-requested consumer ----

    @Test
    void consumerIssuesInvoiceAndSkipsRedelivery() {
        MemoryStores stores = new MemoryStores();
        List<String> outbox = new ArrayList<>();
        BillingSerializer serializer = new JacksonBillingSerializer();
        billing.application.InvoiceService invoices = new billing.application.InvoiceService(
            stores.invoices, stores.payments, stores.accounts, (t, k, p) -> outbox.add(t),
            serializer);
        BillingConsumers consumer = new BillingConsumers(invoices,
            new SmallStores.JdbcProcessedEventStore(db));
        String payload = """
            {"orderId":"ORD-U1","customerId":"CUST-U1","grandTotal":"42.00","currency":"USD"}
            """;
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
            "order.order.invoicerequested.v1", 0, 7, "ORD-U1", payload);
        consumer.onInvoiceRequested(record);
        consumer.onInvoiceRequested(record);
        Optional<Invoice> saved = stores.invoices.find("INV-ORD-U1");
        assertTrue(saved.isPresent());
        assertEquals(InvoiceStatus.ISSUED, saved.orElseThrow().status());
        assertEquals(1, outbox.stream().filter("billing.invoice.issued.v1"::equals).count());
    }

    // ---- outbox relay ----

    @Test
    void relayPublishesAndMarks() {
        OutboxEntryEntity entry = new OutboxEntryEntity("billing.invoice.issued.v1", "INV-R",
            "{}");
        OutboxRepository repo = org.mockito.Mockito.mock(OutboxRepository.class);
        org.mockito.Mockito.when(repo.findUnpublished()).thenReturn(List.of(entry));
        org.mockito.Mockito.when(repo.findById(org.mockito.ArgumentMatchers.any()))
            .thenReturn(java.util.Optional.of(entry));
        @SuppressWarnings("unchecked")
        org.springframework.kafka.core.KafkaTemplate<String, String> kafka =
            org.mockito.Mockito.mock(org.springframework.kafka.core.KafkaTemplate.class);
        org.mockito.Mockito
            .when(kafka.send("billing.invoice.issued.v1", "INV-R", "{}"))
            .thenReturn(java.util.concurrent.CompletableFuture.completedFuture(null));
        OutboxRelay relay = new OutboxRelay(repo, kafka,
            new TransactionTemplate(new NoopTransactionManager()));
        relay.publishPending();
        org.mockito.Mockito.verify(kafka).send("billing.invoice.issued.v1", "INV-R", "{}");
        assertNotNull(entry.getPublishedAt(), "relay must mark the row published");
    }

    // ---- test gateway ----

    @Test
    void gatewayApprovesDeclinesAndFailsHardWhenDown() {
        TestGatewayAdapter gateway = new TestGatewayAdapter(2);
        GatewayResult approved = gateway.authorize("idem-u1", BigDecimal.TEN, "USD");
        assertTrue(approved.approved());
        gateway.declineNext.set(true);
        assertFalse(gateway.capture("idem-u1", approved.refNum(), BigDecimal.TEN).approved());
        gateway.down.set(true);
        assertThrows(GatewayUnavailableException.class,
            () -> gateway.voidAuthorization("idem-u1", approved.refNum()));
        gateway.down.set(false);
        assertTrue(gateway.voidAuthorization("idem-u1", approved.refNum()).approved());
    }

    // ---- PDF renderer ----

    @Test
    void pdfRendererProducesValidInvoiceDocument() {
        Invoice invoice = Invoice.rehydrate("INV-PDF", "ORD-PDF", "CUST-PDF", null, "EUR",
            List.of(new InvoiceLine("1", "INV_FPROD_ITEM", "bracket", BigDecimal.ONE,
                new BigDecimal("3.50"))), InvoiceStatus.ISSUED,
            List.of(new InvoiceStatusChange(InvoiceStatus.DRAFT, Instant.EPOCH, "created")));
        byte[] pdf = new MinimalPdfRenderer().render(invoice);
        String head = new String(pdf, 0, 8, StandardCharsets.US_ASCII);
        assertTrue(head.startsWith("%PDF-1.4"), "must be a PDF, got: " + head);
        String text = new String(pdf, StandardCharsets.US_ASCII);
        assertTrue(text.contains("INV-PDF"));
        assertTrue(text.contains("3.50 EUR"));
    }

    // ---- serializer ----

    @Test
    void serializerCarriesEventContractFields() {
        JacksonBillingSerializer serializer = new JacksonBillingSerializer();
        Invoice invoice = Invoice.rehydrate("INV-S", "ORD-S", "CUST-S", null, "USD",
            List.of(new InvoiceLine("1", "INV_FPROD_ITEM", "w", BigDecimal.ONE,
                BigDecimal.TEN)), InvoiceStatus.SETTLED,
            List.of(new InvoiceStatusChange(InvoiceStatus.DRAFT, Instant.EPOCH, "created")));
        String issued = serializer.serializeInvoice(invoice, "issued");
        assertTrue(issued.contains("\"invoiceId\":\"INV-S\""));
        assertTrue(issued.contains("\"total\":\"10\""));
        Payment payment = Payment.rehydrate("PAY-S", "INV-S", "idem-s", BigDecimal.TEN, "USD",
            PaymentStatus.CAPTURED, "ref-1", "APPROVED");
        String captured = serializer.serializePayment(payment);
        assertTrue(captured.contains("\"paymentId\":\"PAY-S\""));
        assertTrue(captured.contains("\"status\":\"CAPTURED\""));
        assertTrue(captured.contains("\"idempotencyKey\":\"idem-s\""));
    }

    @Test
    void dlqDestinationAppendsSuffix() {
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
            "order.order.invoicerequested.v1", 0, 1, "k", "{}");
        org.apache.kafka.common.TopicPartition destination =
            KafkaConfig.dlqDestination(record, new IllegalStateException("poison"));
        assertEquals("order.order.invoicerequested.v1.dlq", destination.topic());
    }

    @Test
    void pdfTruncatesLongDescriptions() {
        String longDescription = "x".repeat(60);
        Invoice invoice = Invoice.rehydrate("INV-PDF2", null, "CUST-PDF", null, "USD",
            List.of(new InvoiceLine("1", "INV_FPROD_ITEM", longDescription, BigDecimal.ONE,
                BigDecimal.ONE)), InvoiceStatus.DRAFT,
            List.of(new InvoiceStatusChange(InvoiceStatus.DRAFT, Instant.EPOCH, "created")));
        String text = new String(new MinimalPdfRenderer().render(invoice),
            StandardCharsets.US_ASCII);
        assertTrue(text.contains("x".repeat(39)));
    }

    /** In-memory store fakes (billing-application test set is not on this
     *  module's test classpath). */
    static final class MemoryStores {
        final FakeInvoiceStore invoices = new FakeInvoiceStore();
        final billing.application.PaymentStore payments = new FakePaymentStore();
        final billing.application.BillingAccountStore accounts = new FakeAccountStore();
    }

    static final class FakeInvoiceStore implements billing.application.InvoiceStore {
        final List<Invoice> rows = new ArrayList<>();

        @Override
        public Optional<Invoice> find(String invoiceId) {
            return rows.stream().filter(i -> i.invoiceId().equals(invoiceId)).findFirst();
        }

        @Override
        public void save(Invoice invoice) {
            rows.removeIf(i -> i.invoiceId().equals(invoice.invoiceId()));
            rows.add(invoice);
        }
    }

    static final class FakePaymentStore implements billing.application.PaymentStore {
        @Override
        public Optional<Payment> find(String paymentId) {
            return Optional.empty();
        }

        @Override
        public Optional<Payment> findByIdempotencyKey(String idempotencyKey) {
            return Optional.empty();
        }

        @Override
        public List<Payment> capturedForInvoice(String invoiceId) {
            return List.of();
        }

        @Override
        public List<Payment> all() {
            return List.of();
        }

        @Override
        public void save(Payment payment) {
        }
    }

    static final class FakeAccountStore implements billing.application.BillingAccountStore {
        @Override
        public Optional<billing.domain.BillingAccount> find(String billingAccountId) {
            return Optional.empty();
        }

        @Override
        public void save(billing.domain.BillingAccount account) {
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
