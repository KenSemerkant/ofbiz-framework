package billing.adapters.rest.events;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import billing.application.InvoiceService;
import billing.application.ProcessedEventStore;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * P5-F1-S1 ECA consumer: order.order.invoicerequested.v1 (emitted by
 * order-svc's placeOrder saga since P4) issues the invoice. Idempotent via
 * processed_events + the derived invoiceId, so redelivery is a no-op.
 * Gateway outages never land here — this path has no gateway calls; the
 * DLQ policy still guards against a poison payload.
 */
@Component
@ConditionalOnProperty(name = "billing.eca.enabled", havingValue = "true")
public class BillingConsumers {

    private final InvoiceService invoices;
    private final ProcessedEventStore processedEvents;
    private final ObjectMapper json = new ObjectMapper();

    public BillingConsumers(InvoiceService invoices, ProcessedEventStore processedEvents) {
        this.invoices = invoices;
        this.processedEvents = processedEvents;
    }

    @KafkaListener(id = "billing-invoice-requested",
            topics = "order.order.invoicerequested.v1",
            properties = {"auto.offset.reset=earliest"})
    public void onInvoiceRequested(ConsumerRecord<String, String> record) {
        String eventId = "%s:%d:%d".formatted(record.topic(), record.partition(),
            record.offset());
        if (!processedEvents.recordIfAbsent(eventId)) {
            return;
        }
        JsonNode event = json.readTree(record.value());
        invoices.issueFromOrder(event.path("orderId").asText(),
            event.path("customerId").asText(),
            new java.math.BigDecimal(event.path("grandTotal").asText("0")),
            event.path("currency").asText("USD"));
    }
}
