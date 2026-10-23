package finance.adapters.rest.events;

import java.math.BigDecimal;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import finance.application.AutoPostingService;
import finance.application.ProcessedEventStore;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * P5-F2-S2 auto-posting consumer: billing.invoice.issued/settled → journal
 * entry per the configured rule. Idempotent via processed_events + the
 * derived transId; guard failures park for review (never dropped, so they
 * do not reach the DLQ).
 */
@Component
@ConditionalOnProperty(name = "finance.eca.enabled", havingValue = "true")
public class BillingEventConsumer {

    private final AutoPostingService autoPosting;
    private final ProcessedEventStore processedEvents;
    private final ObjectMapper json = new ObjectMapper();

    public BillingEventConsumer(AutoPostingService autoPosting,
            ProcessedEventStore processedEvents) {
        this.autoPosting = autoPosting;
        this.processedEvents = processedEvents;
    }

    @KafkaListener(id = "finance-auto-posting",
            topics = {"billing.invoice.issued.v1", "billing.invoice.settled.v1"},
            properties = {"auto.offset.reset=earliest"})
    public void onBillingEvent(ConsumerRecord<String, String> record) {
        String eventId = "%s:%d:%d".formatted(record.topic(), record.partition(),
            record.offset());
        if (!processedEvents.recordIfAbsent(eventId)) {
            return;
        }
        JsonNode event = json.readTree(record.value());
        autoPosting.onBillingEvent(record.topic(), event.path("invoiceId").asText(),
            new BigDecimal(event.path("total").asText("0")),
            event.has("currency") ? event.path("currency").asText("USD") : "USD",
            "auto " + record.topic() + " " + event.path("invoiceId").asText());
    }
}
