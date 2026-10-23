package billing.adapters.rest.persistence;

import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import billing.application.InvoiceStore;
import billing.domain.Invoice;
import billing.domain.InvoiceStatus;

/** JPA adapter for the InvoiceStore port. */
@Repository
public class JpaInvoiceStore implements InvoiceStore {

    private final InvoiceRepository invoices;

    public JpaInvoiceStore(InvoiceRepository invoices) {
        this.invoices = invoices;
    }

    @Override
    public Optional<Invoice> find(String invoiceId) {
        return invoices.findById(invoiceId).map(this::toDomain);
    }

    @Override
    public void save(Invoice invoice) {
        invoices.save(new InvoiceEntity(invoice.invoiceId(), invoice.orderId(),
            invoice.customerId(), invoice.billingAccountId(), invoice.status().name(),
            InvoiceJson.serializeLines(invoice.lines()),
            InvoiceJson.serializeHistory(invoice.history()), invoice.total(),
            invoice.currencyUomId(),
            invoice.history().isEmpty() ? Instant.EPOCH : invoice.history().get(0).changedAt()));
    }

    private Invoice toDomain(InvoiceEntity entity) {
        return Invoice.rehydrate(entity.getInvoiceId(), entity.getOrderId(),
            entity.getCustomerId(), entity.getBillingAccountId(), entity.getCurrencyUomId(),
            InvoiceJson.deserializeLines(entity.getLines()),
            InvoiceStatus.valueOf(entity.getStatus()),
            InvoiceJson.deserializeHistory(entity.getHistory()));
    }
}
