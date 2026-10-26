package billing.application;

import java.util.List;
import java.util.Optional;
import billing.domain.Invoice;

/** Persistence port for the Invoice aggregate. */
public interface InvoiceStore {

    Optional<Invoice> find(String invoiceId);

    /** P9-F1: invoice listing for the SPA accounting area. */
    List<Invoice> all();

    void save(Invoice invoice);
}
