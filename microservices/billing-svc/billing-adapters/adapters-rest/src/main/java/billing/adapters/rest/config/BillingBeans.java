package billing.adapters.rest.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import billing.application.BillingSerializer;
import billing.application.InvoiceService;
import billing.application.InvoiceStore;
import billing.application.OutboxWriter;
import billing.application.PaymentGateway;
import billing.application.PaymentService;
import billing.application.BillingAccountStore;

/** Wires the application-layer use cases into the adapter layer. */
@Configuration
@EnableScheduling
public class BillingBeans {

    @Bean
    InvoiceService invoiceService(InvoiceStore invoices,
            billing.application.PaymentStore payments, BillingAccountStore accounts,
            OutboxWriter outbox, BillingSerializer serializer) {
        return new InvoiceService(invoices, payments, accounts, outbox, serializer);
    }

    @Bean
    PaymentService paymentService(billing.application.PaymentStore payments,
            InvoiceStore invoices, billing.application.GatewayAttemptStore attempts,
            PaymentGateway gateway, OutboxWriter outbox, BillingSerializer serializer,
            InvoiceService invoices2) {
        return new PaymentService(payments, invoices, attempts, gateway, outbox, serializer,
            invoices2);
    }
}
