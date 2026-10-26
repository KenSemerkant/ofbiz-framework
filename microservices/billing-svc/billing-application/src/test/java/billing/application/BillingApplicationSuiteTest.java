package billing.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import billing.domain.BillingAccount;
import billing.domain.GatewayResult;
import billing.domain.Invoice;
import billing.domain.InvoiceLine;
import billing.domain.InvoiceStatus;
import billing.domain.Payment;
import billing.domain.PaymentStatus;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

/**
 * P5-F1 use-case suite: the invoice/payment/credit-limit contracts that the
 * gate e2e exercises on-cluster. Every event the outbox collects here is a
 * topic another service (finance-svc, notification-svc) depends on.
 */
class BillingApplicationSuiteTest {

    private static final BigDecimal PRICE = new BigDecimal("22.00");

    private final FakeStores stores = new FakeStores();
    private final ScriptedGateway gateway = new ScriptedGateway();
    private final List<String> outbox = new ArrayList<>();
    private final InvoiceService invoices;
    private final PaymentService payments;

    BillingApplicationSuiteTest() {
        invoices = new InvoiceService(stores.invoices, stores.payments, stores.accounts,
            (type, key, payload) -> outbox.add(type + ":" + key),
            new BillingSerializer() { public String serializeInvoice(Invoice i, String n) { return "invoice:" + i.invoiceId(); } public String serializePayment(Payment p) { return "payment:" + p.paymentId(); } public String serializeInvoiceNotification(Invoice i) { return "notif:" + i.invoiceId(); } });
        payments = new PaymentService(stores.payments, stores.invoices, stores.gatewayAttempts,
            gateway, (type, key, payload) -> outbox.add(type + ":" + key),
            new BillingSerializer() { public String serializeInvoice(Invoice i, String n) { return "invoice:" + i.invoiceId(); } public String serializePayment(Payment p) { return "payment:" + p.paymentId(); } public String serializeInvoiceNotification(Invoice i) { return "notif:" + i.invoiceId(); } }, invoices);
    }

    private static InvoiceLine line() {
        return new InvoiceLine("1", "INV_FPROD_ITEM", "widget", BigDecimal.ONE, PRICE);
    }

    @Test
    void orderRequestIssuesInvoiceOnceWithEvent() {
        Invoice invoice = invoices.issueFromOrder("ORD-9", "CUST-1", PRICE, "USD");
        assertEquals(InvoiceStatus.ISSUED, invoice.status());
        assertTrue(outbox.contains("billing.invoice.issued.v1:INV-ORD-9")
            || outbox.stream().anyMatch(e -> e.startsWith("billing.invoice.issued.v1")));
        Invoice again = invoices.issueFromOrder("ORD-9", "CUST-1", PRICE, "USD");
        assertEquals(invoice.invoiceId(), again.invoiceId());
        long issuedEvents = outbox.stream()
            .filter(e -> e.startsWith("billing.invoice.issued.v1")).count();
        assertEquals(1, issuedEvents);
    }

    @Test
    void creditLimitBlocksIssueAndEmitsNothing() {
        stores.accounts.save(BillingAccount.rehydrate("BA-1", "CUST-1",
            new BigDecimal("10.00"), BigDecimal.TEN, 30));
        Invoice invoice = invoices.createDraft("INV-C", "CUST-1", "USD", "BA-1",
            List.of(line()));
        assertThrows(billing.domain.CreditLimitExceededException.class,
            () -> invoices.issue("INV-C"));
        assertEquals(InvoiceStatus.DRAFT, stores.invoices.find("INV-C").orElseThrow().status());
        assertTrue(outbox.isEmpty());
    }

    @Test
    void captureSettlesFullyPaidInvoiceAndReleasesAr() {
        stores.accounts.save(BillingAccount.rehydrate("BA-1", "CUST-1",
            new BigDecimal("100.00"), BigDecimal.ZERO, 30));
        Invoice invoice = invoices.createDraft("INV-P", "CUST-1", "USD", "BA-1",
            List.of(line()));
        invoices.issue("INV-P");
        Payment payment = payments.create("PAY-1", "INV-P", "idem-1", PRICE, "USD");
        Payment again = payments.create("PAY-2", "INV-P", "idem-1", PRICE, "USD");
        assertEquals("PAY-1", again.paymentId());
        payments.authorize("PAY-1");
        payments.capture("PAY-1");
        assertEquals(PaymentStatus.CAPTURED, stores.payments.find("PAY-1").orElseThrow()
            .status());
        assertEquals(InvoiceStatus.SETTLED, stores.invoices.find("INV-P").orElseThrow()
            .status());
        assertEquals(0, stores.accounts.find("BA-1").orElseThrow().outstandingArBalance()
            .compareTo(BigDecimal.ZERO));
        assertTrue(outbox.stream().anyMatch(e -> e.startsWith("billing.payment.captured.v1")));
        assertTrue(outbox.stream().anyMatch(e -> e.startsWith("billing.invoice.settled.v1")));
    }

    @Test
    void partialCaptureKeepsInvoiceIssued() {
        Invoice invoice = invoices.issueFromOrder("ORD-P", "CUST-1", PRICE, "USD");
        Payment payment = payments.create("PAY-H", "INV-ORD-P", "idem-h", new BigDecimal("5.00"),
            "USD");
        payments.authorize("PAY-H");
        payments.capture("PAY-H");
        assertEquals(PaymentStatus.CAPTURED, payment.status());
        assertEquals(InvoiceStatus.ISSUED, stores.invoices.find("INV-ORD-P").orElseThrow()
            .status());
    }

    @Test
    void gatewayOutagePropagatesAndShowsInReconciliation() {
        Invoice invoice = invoices.issueFromOrder("ORD-O", "CUST-1", PRICE, "USD");
        Payment payment = payments.create("PAY-O", "INV-ORD-O", "idem-o", PRICE, "USD");
        gateway.down.set(true);
        assertThrows(GatewayUnavailableException.class, () -> payments.authorize("PAY-O"));
        assertEquals(1, stores.gatewayAttempts.all().size());
        assertEquals("UNAVAILABLE", stores.gatewayAttempts.all().get(0).resultCode());
        List<PaymentService.ReconciliationRow> report = payments.reconciliationReport();
        assertEquals(1, report.size());
        assertEquals("PAY-O", report.get(0).paymentId());
        assertEquals(1, report.get(0).attempts());
        gateway.down.set(false);
        Payment recovered = payments.authorize("PAY-O");
        assertEquals(PaymentStatus.AUTHORIZED, recovered.status());
        assertEquals(0, payments.reconciliationReport().size());
    }

    @Test
    void declinedAuthorizeFailsPaymentWithTypedEvent() {
        invoices.issueFromOrder("ORD-D", "CUST-1", PRICE, "USD");
        payments.create("PAY-D", "INV-ORD-D", "idem-d", PRICE, "USD");
        gateway.declineNext.set(true);
        Payment payment = payments.authorize("PAY-D");
        assertEquals(PaymentStatus.FAILED, payment.status());
        assertTrue(outbox.stream().anyMatch(e -> e.startsWith("billing.payment.failed.v1")));
    }

    @Test
    void voidReleasesAuthorizationWithoutCapturing() {
        invoices.issueFromOrder("ORD-V", "CUST-1", PRICE, "USD");
        payments.create("PAY-V", "INV-ORD-V", "idem-v", PRICE, "USD");
        payments.authorize("PAY-V");
        Payment payment = payments.voidAuthorization("PAY-V");
        assertEquals(PaymentStatus.VOIDED, payment.status());
        assertEquals(InvoiceStatus.ISSUED, stores.invoices.find("INV-ORD-V").orElseThrow()
            .status());
        assertEquals(0, payments.reconciliationReport().size());
    }

    @Test
    void cancelWriteOffAndMissingLookups() {
        Invoice issued = invoices.issueFromOrder("ORD-C", "CUST-1", PRICE, "USD");
        assertTrue(invoices.get("INV-ORD-C").isPresent());
        assertEquals(InvoiceStatus.CANCELLED, invoices.cancel("INV-ORD-C", "voided order")
            .status());
        Invoice manual = invoices.createDraft("INV-W", "CUST-1", "USD", null, List.of(line()));
        invoices.issue("INV-W");
        assertEquals(InvoiceStatus.WRITTEN_OFF, invoices.writeOff("INV-W", "bad debt")
            .status());
        assertThrows(java.util.NoSuchElementException.class, () -> invoices.issue("INV-404"));
        payments.create("PAY-N", "INV-ORD-C", "idem-n", PRICE, "USD");
        assertTrue(payments.get("PAY-N").isPresent());
        assertThrows(java.util.NoSuchElementException.class, () -> payments.authorize("PAY-404"));
    }

    @Test
    void captureDeclineFailsPaymentAndKeepsInvoiceIssued() {
        invoices.issueFromOrder("ORD-DC", "CUST-1", PRICE, "USD");
        payments.create("PAY-DC", "INV-ORD-DC", "idem-dc", PRICE, "USD");
        payments.authorize("PAY-DC");
        gateway.declineNext.set(true);
        Payment payment = payments.capture("PAY-DC");
        assertEquals(PaymentStatus.FAILED, payment.status());
        assertEquals(InvoiceStatus.ISSUED, stores.invoices.find("INV-ORD-DC").orElseThrow()
            .status());
    }

    @Test
    void voidDeclineFailsPayment() {
        invoices.issueFromOrder("ORD-DV", "CUST-1", PRICE, "USD");
        payments.create("PAY-DV", "INV-ORD-DV", "idem-dv", PRICE, "USD");
        payments.authorize("PAY-DV");
        gateway.declineNext.set(true);
        assertEquals(PaymentStatus.FAILED, payments.voidAuthorization("PAY-DV").status());
    }

    @Test
    void settleIfFullyAppliedIsNoopForNonIssuedInvoice() {
        Invoice invoice = invoices.issueFromOrder("ORD-NI", "CUST-1", PRICE, "USD");
        invoices.cancel("INV-ORD-NI", "test");
        assertEquals(false, invoices.settleIfFullyApplied(stores.invoices
            .find("INV-ORD-NI").orElseThrow()));
    }

    /** In-memory fakes for every store port — the use cases stay pure. */
    private static final class FakeStores {
        final FakeInvoiceStore invoices = new FakeInvoiceStore();
        final FakePaymentStore payments = new FakePaymentStore();
        final FakeAccountStore accounts = new FakeAccountStore();
        final FakeGatewayAttemptStore gatewayAttempts = new FakeGatewayAttemptStore();
    }

    static final class FakeInvoiceStore implements InvoiceStore {
        final List<Invoice> rows = new ArrayList<>();

        @Override
        public Optional<Invoice> find(String invoiceId) {
            return rows.stream().filter(i -> i.invoiceId().equals(invoiceId)).findFirst();
        }

        @Override
        public List<Invoice> all() {
            return rows.stream()
                .sorted(java.util.Comparator.comparing(Invoice::invoiceId)).toList();
        }

        @Override
        public void save(Invoice invoice) {
            rows.removeIf(i -> i.invoiceId().equals(invoice.invoiceId()));
            rows.add(invoice);
        }
    }

    static final class FakePaymentStore implements PaymentStore {
        final List<Payment> rows = new ArrayList<>();

        @Override
        public Optional<Payment> find(String paymentId) {
            return rows.stream().filter(p -> p.paymentId().equals(paymentId)).findFirst();
        }

        @Override
        public Optional<Payment> findByIdempotencyKey(String idempotencyKey) {
            return rows.stream().filter(p -> p.idempotencyKey().equals(idempotencyKey))
                .findFirst();
        }

        @Override
        public List<Payment> capturedForInvoice(String invoiceId) {
            return rows.stream()
                .filter(p -> p.invoiceId().equals(invoiceId)
                    && p.status() == PaymentStatus.CAPTURED)
                .toList();
        }

        @Override
        public List<Payment> all() {
            return List.copyOf(rows);
        }

        @Override
        public void save(Payment payment) {
            rows.removeIf(p -> p.paymentId().equals(payment.paymentId()));
            rows.add(payment);
        }
    }

    static final class FakeAccountStore implements BillingAccountStore {
        final List<BillingAccount> rows = new ArrayList<>();

        @Override
        public Optional<BillingAccount> find(String billingAccountId) {
            return rows.stream().filter(a -> a.billingAccountId().equals(billingAccountId))
                .findFirst();
        }

        @Override
        public void save(BillingAccount account) {
            rows.removeIf(a -> a.billingAccountId().equals(account.billingAccountId()));
            rows.add(account);
        }
    }

    static final class FakeGatewayAttemptStore implements GatewayAttemptStore {
        final List<billing.domain.GatewayAttempt> rows = new ArrayList<>();

        @Override
        public void append(billing.domain.GatewayAttempt attempt) {
            rows.add(attempt);
        }

        @Override
        public List<billing.domain.GatewayAttempt> all() {
            return List.copyOf(rows);
        }
    }

    /** Deterministic test gateway: flags flip its behavior like the adapter. */
    static final class ScriptedGateway implements PaymentGateway {
        final AtomicBoolean down = new AtomicBoolean();
        final AtomicBoolean declineNext = new AtomicBoolean();
        int calls;

        private GatewayResult answer(String op, String ref) {
            calls++;
            if (down.get()) {
                throw new GatewayUnavailableException(op, 3,
                    new IllegalStateException("connection refused"));
            }
            if (declineNext.compareAndSet(true, false)) {
                return GatewayResult.declined("DECLINED");
            }
            return GatewayResult.approved(ref);
        }

        @Override
        public GatewayResult authorize(String idempotencyKey, BigDecimal amount,
                String currencyUomId) {
            return answer("authorize", "auth-" + idempotencyKey);
        }

        @Override
        public GatewayResult capture(String idempotencyKey, String refNum, BigDecimal amount) {
            return answer("capture", refNum);
        }

        @Override
        public GatewayResult voidAuthorization(String idempotencyKey, String refNum) {
            return answer("void", refNum);
        }
    }
}
