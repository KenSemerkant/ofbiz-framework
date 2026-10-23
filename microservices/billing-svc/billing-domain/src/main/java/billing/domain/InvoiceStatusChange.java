package billing.domain;

import java.time.Instant;

/** One row of the append-only status history (mirrors OFBiz InvoiceStatus). */
public record InvoiceStatusChange(InvoiceStatus status, Instant changedAt, String note) {
}
