package billing.adapters.rest.persistence;

import org.springframework.stereotype.Component;
import billing.application.BillingSerializer;
import billing.domain.Invoice;
import billing.domain.Payment;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/** Event payloads for the billing.* topics (Jackson 3). */
@Component
public class JacksonBillingSerializer implements BillingSerializer {

    private final ObjectMapper json = new ObjectMapper();

    @Override
    public String serializeInvoice(Invoice invoice, String note) {
        ObjectNode node = json.createObjectNode();
        node.put("invoiceId", invoice.invoiceId());
        if (invoice.orderId() != null) {
            node.put("orderId", invoice.orderId());
        }
        node.put("customerId", invoice.customerId());
        node.put("status", invoice.status().name());
        node.put("total", invoice.total().toPlainString());
        node.put("currency", invoice.currencyUomId());
        node.put("lineCount", invoice.lines().size());
        node.put("note", note);
        return node.toString();
    }

    @Override
    public String serializeInvoiceNotification(Invoice invoice) {
        ObjectNode node = json.createObjectNode();
        node.put("notificationId", "NOTIF-" + invoice.invoiceId());
        node.put("channel", "EMAIL");
        node.put("recipient", "customer-" + invoice.customerId() + "@example.invalid");
        node.put("template", "invoice-issued");
        node.put("locale", "en");
        ObjectNode params = node.putObject("params");
        params.put("invoiceId", invoice.invoiceId());
        params.put("total", invoice.total().toPlainString());
        params.put("currency", invoice.currencyUomId());
        return node.toString();
    }

    @Override
    public String serializePayment(Payment payment) {
        ObjectNode node = json.createObjectNode();
        node.put("paymentId", payment.paymentId());
        node.put("invoiceId", payment.invoiceId());
        node.put("status", payment.status().name());
        node.put("amount", payment.amount().toPlainString());
        node.put("currency", payment.currencyUomId());
        node.put("idempotencyKey", payment.idempotencyKey());
        if (payment.gatewayRef() != null) {
            node.put("gatewayRef", payment.gatewayRef());
        }
        node.put("resultCode", payment.resultCode());
        return node.toString();
    }
}
