package billing.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import billing.domain.Invoice;
import billing.domain.InvoiceLine;

/**
 * P5-F1-S1/S3 invoice use case: guarded lifecycle + one outbox event per
 * state change; issue runs the BillingAccount credit check and books AR in
 * the same logical operation, settle releases it again.
 */
public class InvoiceService {

    public static final String INVOICE_ISSUED = "billing.invoice.issued.v1";
    public static final String NOTIFICATION_REQUESTED = "notification.requested.v1";
    public static final String INVOICE_SETTLED = "billing.invoice.settled.v1";

    private final InvoiceStore invoices;
    private final PaymentStore payments;
    private final BillingAccountStore accounts;
    private final OutboxWriter outbox;
    private final BillingSerializer serializer;

    public InvoiceService(InvoiceStore invoices, PaymentStore payments,
            BillingAccountStore accounts, OutboxWriter outbox, BillingSerializer serializer) {
        this.invoices = invoices;
        this.payments = payments;
        this.accounts = accounts;
        this.outbox = outbox;
        this.serializer = serializer;
    }

    public Optional<Invoice> get(String invoiceId) {
        return invoices.find(invoiceId);
    }

    /** P9-F1: invoice listing for the SPA accounting area. */
    public List<Invoice> all() {
        return invoices.all();
    }

    /** Manual invoice creation (P5-F1-S1): starts in DRAFT. */
    public Invoice createDraft(String invoiceId, String customerId, String currencyUomId,
            String billingAccountId, List<InvoiceLine> lines) {
        Invoice invoice = new Invoice(invoiceId, null, customerId, billingAccountId,
            currencyUomId, lines, Instant.now());
        invoices.save(invoice);
        return invoice;
    }

    /**
     * ECA path (P4 handshake): order.order.invoicerequested → issued invoice.
     * Idempotent by construction — the derived invoiceId is stable per order.
     */
    public Invoice issueFromOrder(String orderId, String customerId, BigDecimal grandTotal,
            String currencyUomId) {
        String invoiceId = "INV-" + orderId;
        Optional<Invoice> existing = invoices.find(invoiceId);
        if (existing.isPresent()) {
            return existing.orElseThrow();
        }
        Invoice invoice = new Invoice(invoiceId, orderId, customerId, null, currencyUomId,
            List.of(new InvoiceLine("00001", "INV_FPROD_ITEM", "order " + orderId,
                BigDecimal.ONE, grandTotal)), Instant.now());
        issue(invoice);
        return invoice;
    }

    public Invoice issue(String invoiceId) {
        Invoice invoice = load(invoiceId);
        issue(invoice);
        return invoice;
    }

    private void issue(Invoice invoice) {
        if (invoice.billingAccountId() != null) {
            billing.domain.BillingAccount account = accounts
                .find(invoice.billingAccountId()).orElseThrow();
            account.addAr(invoice.total());
            accounts.save(account);
        }
        invoice.issue(Instant.now());
        invoices.save(invoice);
        outbox.append(INVOICE_ISSUED, invoice.invoiceId(), serializer.serializeInvoice(invoice,
            "issued"));
        // CG-5: the invoice email rides the notification platform service —
        // same outbox tx, so an issued invoice always requests its email.
        outbox.append(NOTIFICATION_REQUESTED, invoice.invoiceId(),
            serializer.serializeInvoiceNotification(invoice));
    }

    /**
     * P5-F1-S2: after a captured payment, settle when the invoice is fully
     * applied. Underpaid invoices stay ISSUED — never silently settled.
     */
    public boolean settleIfFullyApplied(Invoice invoice) {
        if (invoice.status() != billing.domain.InvoiceStatus.ISSUED) {
            return false;
        }
        BigDecimal captured = payments.capturedForInvoice(invoice.invoiceId()).stream()
            .map(billing.domain.Payment::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (captured.compareTo(invoice.total()) < 0) {
            return false;
        }
        if (invoice.billingAccountId() != null) {
            billing.domain.BillingAccount account = accounts
                .find(invoice.billingAccountId()).orElseThrow();
            account.reduceAr(invoice.total());
            accounts.save(account);
        }
        invoice.settle(captured, Instant.now());
        invoices.save(invoice);
        outbox.append(INVOICE_SETTLED, invoice.invoiceId(),
            serializer.serializeInvoice(invoice, "settled"));
        return true;
    }

    public Invoice cancel(String invoiceId, String note) {
        return transition(invoiceId, invoice -> invoice.cancel(Instant.now(), note), "cancelled");
    }

    public Invoice writeOff(String invoiceId, String note) {
        return transition(invoiceId, invoice -> invoice.writeOff(Instant.now(), note),
            "written off");
    }

    @FunctionalInterface
    private interface Mutation {
        void apply(Invoice invoice);
    }

    private Invoice transition(String invoiceId, Mutation mutation, String note) {
        Invoice invoice = load(invoiceId);
        mutation.apply(invoice);
        invoices.save(invoice);
        return invoice;
    }

    private Invoice load(String invoiceId) {
        return invoices.find(invoiceId)
            .orElseThrow(() -> new NoSuchElementException("no invoice " + invoiceId));
    }
}
