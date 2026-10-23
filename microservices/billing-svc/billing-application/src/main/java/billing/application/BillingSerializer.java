package billing.application;

import billing.domain.Invoice;
import billing.domain.Payment;

/** Event-payload port: the application layer never touches Jackson. */
public interface BillingSerializer {

    String serializeInvoice(Invoice invoice, String note);

    String serializePayment(Payment payment);

    /** notification.requested.v1 wire shape for the invoice email (P5). */
    String serializeInvoiceNotification(Invoice invoice);
}
