package billing.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Invoice aggregate root (P5-F1-S1): lines are captured from the order
 * snapshot; every lifecycle transition is guarded by {@link InvoiceStatus}
 * and appends to the history. Settle is additionally guarded by the applied
 * payment total — an invoice can never settle underpaid.
 */
public final class Invoice {

    private final String invoiceId;
    private final String orderId;
    private final String customerId;
    private final String billingAccountId;
    private final String currencyUomId;
    private final List<InvoiceLine> lines;
    private InvoiceStatus status;
    private final List<InvoiceStatusChange> history = new ArrayList<>();

    public Invoice(String invoiceId, String orderId, String customerId,
            String billingAccountId, String currencyUomId, List<InvoiceLine> lines,
            Instant createdAt) {
        if (invoiceId == null || invoiceId.isBlank()) {
            throw new IllegalArgumentException("invoiceId must not be blank");
        }
        if (customerId == null || customerId.isBlank()) {
            throw new IllegalArgumentException("customerId must not be blank");
        }
        if (currencyUomId == null || currencyUomId.isBlank()) {
            throw new IllegalArgumentException("currencyUomId must not be blank");
        }
        if (lines == null || lines.isEmpty()) {
            throw new IllegalArgumentException("an invoice needs at least one line");
        }
        this.invoiceId = invoiceId;
        this.orderId = orderId;
        this.customerId = customerId;
        this.billingAccountId = billingAccountId;
        this.currencyUomId = currencyUomId;
        this.lines = List.copyOf(lines);
        this.status = InvoiceStatus.DRAFT;
        this.history.add(new InvoiceStatusChange(InvoiceStatus.DRAFT, createdAt, "created"));
    }

    /** DRAFT → ISSUED: the document becomes receivable. */
    public void issue(Instant at) {
        transition(InvoiceStatus.ISSUED, at, "issued");
    }

    /** ISSUED → SETTLED, only once applied payments cover the total. */
    public void settle(BigDecimal appliedTotal, Instant at) {
        if (appliedTotal == null || appliedTotal.compareTo(total()) < 0) {
            throw new IllegalStateException("invoice %s is not fully paid: applied %s < total %s"
                .formatted(invoiceId, appliedTotal, total()));
        }
        transition(InvoiceStatus.SETTLED, at, "settled");
    }

    public void cancel(Instant at, String note) {
        transition(InvoiceStatus.CANCELLED, at, note == null ? "cancelled" : note);
    }

    public void writeOff(Instant at, String note) {
        transition(InvoiceStatus.WRITTEN_OFF, at, note == null ? "written off" : note);
    }

    private void transition(InvoiceStatus to, Instant at, String note) {
        InvoiceStatus.requireTransition(status, to);
        status = to;
        history.add(new InvoiceStatusChange(to, at, note));
    }

    /** Rehydration factory: the persisted status/history are trusted. */
    public static Invoice rehydrate(String invoiceId, String orderId, String customerId,
            String billingAccountId, String currencyUomId, List<InvoiceLine> lines,
            InvoiceStatus status, List<InvoiceStatusChange> history) {
        Invoice invoice = new Invoice(invoiceId, orderId, customerId, billingAccountId,
            currencyUomId, lines, history.isEmpty() ? Instant.EPOCH : history.get(0).changedAt());
        invoice.status = status;
        invoice.history.clear();
        invoice.history.addAll(history);
        return invoice;
    }

    public BigDecimal total() {
        return lines.stream().map(InvoiceLine::lineTotal)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public String invoiceId() {
        return invoiceId;
    }

    /** Referencing order; null for manual invoices (ID-only cross-context ref). */
    public String orderId() {
        return orderId;
    }

    public String customerId() {
        return customerId;
    }

    /** BillingAccount for the credit-limit check (P5-F1-S3); may be null. */
    public String billingAccountId() {
        return billingAccountId;
    }

    public String currencyUomId() {
        return currencyUomId;
    }

    public List<InvoiceLine> lines() {
        return lines;
    }

    public InvoiceStatus status() {
        return status;
    }

    public List<InvoiceStatusChange> history() {
        return List.copyOf(history);
    }
}
