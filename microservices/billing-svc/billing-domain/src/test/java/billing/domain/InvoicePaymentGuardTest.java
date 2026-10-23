package billing.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * P5-F1-S1/S2 guard tests: the status machines are the invoice and payment
 * contracts — every assertion here is a 409 the API must produce and a
 * ledger mistake the service must refuse to make.
 */
class InvoicePaymentGuardTest {

    private static Invoice invoice() {
        return new Invoice("INV-1", "ORD-1", "CUST-1", null, "USD",
            List.of(new InvoiceLine("1", "INV_FPROD_ITEM", "widget", new BigDecimal("2"),
                new BigDecimal("11.00"))), Instant.parse("2026-01-01T00:00:00Z"));
    }

    @Test
    void issueThenSettleIsLegalAndAppendsHistory() {
        Invoice invoice = invoice();
        invoice.issue(Instant.now());
        invoice.settle(new BigDecimal("22.00"), Instant.now());
        assertEquals(InvoiceStatus.SETTLED, invoice.status());
        assertEquals(3, invoice.history().size());
        assertEquals(0, invoice.total().compareTo(new BigDecimal("22.00")));
        assertEquals("INV-1", invoice.invoiceId());
        assertEquals("ORD-1", invoice.orderId());
        assertEquals("CUST-1", invoice.customerId());
        assertEquals("USD", invoice.currencyUomId());
        assertEquals(1, invoice.lines().size());
    }

    @Test
    void underpaidInvoiceRefusesToSettle() {
        Invoice invoice = invoice();
        invoice.issue(Instant.now());
        assertThrows(IllegalStateException.class,
            () -> invoice.settle(new BigDecimal("21.99"), Instant.now()));
        assertEquals(InvoiceStatus.ISSUED, invoice.status());
    }

    @Test
    void illegalTransitionsThrowTypedError() {
        Invoice invoice = invoice();
        assertThrows(IllegalInvoiceTransitionException.class,
            () -> invoice.settle(new BigDecimal("22.00"), Instant.now()));
        invoice.issue(Instant.now());
        invoice.settle(new BigDecimal("22.00"), Instant.now());
        assertThrows(IllegalInvoiceTransitionException.class,
            () -> invoice.cancel(Instant.now(), null));
    }

    @Test
    void writeOffOnlyFromIssued() {
        Invoice invoice = invoice();
        assertThrows(IllegalInvoiceTransitionException.class,
            () -> invoice.writeOff(Instant.now(), null));
        invoice.issue(Instant.now());
        invoice.writeOff(Instant.now(), null);
        assertEquals(InvoiceStatus.WRITTEN_OFF, invoice.status());
    }

    @Test
    void rehydrateTrustedStateAndGuardsContinue() {
        Invoice invoice = Invoice.rehydrate("INV-2", null, "CUST-1", "BA-1", "EUR",
            List.of(new InvoiceLine("1", "INV_FPROD_ITEM", "w", BigDecimal.ONE,
                BigDecimal.TEN)), InvoiceStatus.ISSUED,
            List.of(new InvoiceStatusChange(InvoiceStatus.DRAFT, Instant.EPOCH, "created")));
        assertEquals(InvoiceStatus.ISSUED, invoice.status());
        assertEquals("BA-1", invoice.billingAccountId());
        invoice.settle(BigDecimal.TEN, Instant.now());
        assertEquals(InvoiceStatus.SETTLED, invoice.status());
    }

    @Test
    void rehydrateWithEmptyHistoryUsesEpoch() {
        Invoice invoice = Invoice.rehydrate("INV-3", null, "CUST-1", null, "USD",
            List.of(new InvoiceLine("1", "T", "d", BigDecimal.ONE, BigDecimal.TEN)),
            InvoiceStatus.DRAFT, List.of());
        assertEquals(InvoiceStatus.DRAFT, invoice.status());
    }

    @Test
    void cancelWithNullNoteDefaultsIt() {
        Invoice invoice = invoice();
        invoice.cancel(Instant.now(), null);
        assertEquals(InvoiceStatus.CANCELLED, invoice.status());
        assertEquals("cancelled", invoice.history().get(1).note());
    }

    @Test
    void nullAppliedTotalRefusesSettle() {
        Invoice invoice = invoice();
        invoice.issue(Instant.now());
        assertThrows(IllegalStateException.class, () -> invoice.settle(null, Instant.now()));
        assertEquals(InvoiceStatus.ISSUED, invoice.status());
    }

    @Test
    void lineConstructionGuards() {
        assertThrows(IllegalArgumentException.class,
            () -> new InvoiceLine("1", " ", "d", BigDecimal.ONE, BigDecimal.TEN));
        assertThrows(IllegalArgumentException.class,
            () -> new InvoiceLine("1", "T", "d", BigDecimal.ZERO, BigDecimal.TEN));
        assertThrows(IllegalArgumentException.class,
            () -> new InvoiceLine("1", "T", "d", BigDecimal.ONE, null));
        InvoiceLine line = new InvoiceLine("1", "T", "d", BigDecimal.ONE, BigDecimal.TEN);
        assertEquals("d", line.description());
        assertEquals(0, line.lineTotal().compareTo(BigDecimal.TEN));
    }

    @Test
    void blankIdsAndEmptyLinesRejected() {
        assertThrows(IllegalArgumentException.class,
            () -> new Invoice("", "ORD-1", "CUST-1", null, "USD",
                List.of(new InvoiceLine("1", "T", "d", BigDecimal.ONE, BigDecimal.TEN)),
                Instant.EPOCH));
        assertThrows(IllegalArgumentException.class,
            () -> new Invoice("INV-1", "ORD-1", "CUST-1", null, "USD", List.of(),
                Instant.EPOCH));
        assertThrows(IllegalArgumentException.class,
            () -> new InvoiceLine("", "T", "d", BigDecimal.ONE, BigDecimal.TEN));
        assertThrows(IllegalArgumentException.class,
            () -> new Invoice("INV-1", "ORD-1", " ", null, "USD",
                List.of(new InvoiceLine("1", "T", "d", BigDecimal.ONE, BigDecimal.TEN)),
                Instant.EPOCH));
        assertThrows(IllegalArgumentException.class,
            () -> new Invoice("INV-1", "ORD-1", "CUST-1", null, " ",
                List.of(new InvoiceLine("1", "T", "d", BigDecimal.ONE, BigDecimal.TEN)),
                Instant.EPOCH));
        assertThrows(IllegalArgumentException.class,
            () -> new Payment("PAY-1", "INV-1", "k", BigDecimal.ONE, " ", Instant.EPOCH));
    }

    @Test
    void paymentHappyPathAuthorizeCapture() {
        Payment payment = new Payment("PAY-1", "INV-1", "idem-1",
            new BigDecimal("22.00"), "USD", Instant.now());
        payment.authorize("gw-ref-1", "APPROVED");
        payment.capture("CAPTURED");
        assertEquals(PaymentStatus.CAPTURED, payment.status());
        assertEquals("gw-ref-1", payment.gatewayRef());
        assertEquals("CAPTURED", payment.resultCode());
        assertThrows(IllegalPaymentTransitionException.class,
            () -> payment.voidAuthorization("VOID"));
    }

    @Test
    void authorizedPaymentCanBeVoided() {
        Payment payment = new Payment("PAY-1", "INV-1", "idem-1",
            new BigDecimal("22.00"), "USD", Instant.now());
        payment.authorize("gw-ref-1", "APPROVED");
        payment.voidAuthorization("VOIDED");
        assertEquals(PaymentStatus.VOIDED, payment.status());
    }

    @Test
    void captureWithoutAuthorizationThrows() {
        Payment payment = new Payment("PAY-1", "INV-1", "idem-1",
            new BigDecimal("22.00"), "USD", Instant.now());
        assertThrows(IllegalPaymentTransitionException.class,
            () -> payment.capture("APPROVED"));
        payment.fail("DECLINED");
        assertEquals(PaymentStatus.FAILED, payment.status());
        assertEquals("PAY-1", payment.paymentId());
        assertEquals("INV-1", payment.invoiceId());
        assertEquals("idem-1", payment.idempotencyKey());
        assertEquals(0, payment.amount().compareTo(new BigDecimal("22.00")));
        assertEquals("USD", payment.currencyUomId());
    }

    @Test
    void rehydratedPaymentResumesAtCaptured() {
        Payment payment = Payment.rehydrate("PAY-2", "INV-1", "idem-2",
            new BigDecimal("5.00"), "USD", PaymentStatus.CAPTURED, "gw-ref-2", "OK");
        assertThrows(IllegalPaymentTransitionException.class,
            () -> payment.capture("OK"));
    }

    @Test
    void paymentConstructionGuards() {
        assertThrows(IllegalArgumentException.class,
            () -> new Payment("", "INV-1", "k", BigDecimal.ONE, "USD", Instant.EPOCH));
        assertThrows(IllegalArgumentException.class,
            () -> new Payment("PAY-1", "INV-1", "k", BigDecimal.ZERO, "USD", Instant.EPOCH));
        assertThrows(IllegalArgumentException.class,
            () -> new Payment("PAY-1", "INV-1", " ", BigDecimal.ONE, "USD", Instant.EPOCH));
        assertTrue(InvoiceStatus.canTransition(InvoiceStatus.DRAFT, InvoiceStatus.ISSUED));
        assertTrue(PaymentStatus.canTransition(PaymentStatus.AUTHORIZED, PaymentStatus.CAPTURED));
    }
}
