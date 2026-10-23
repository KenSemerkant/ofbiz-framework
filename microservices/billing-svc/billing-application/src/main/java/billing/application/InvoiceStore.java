package billing.application;

import java.util.Optional;
import billing.domain.Invoice;

/** Persistence port for the Invoice aggregate. */
public interface InvoiceStore {

    Optional<Invoice> find(String invoiceId);

    void save(Invoice invoice);
}
