package marketing.adapters.rest.events;

import java.time.Instant;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import marketing.application.AttributionService;
import marketing.application.ProcessedEventStore;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * P6-T5 attribution consumer: order.order.placed.v1 → attribution row.
 * The placed payload is the OrderPlaced.avsc contract plus the optional
 * trackingCode field order-svc appends (P4-F2-S2 backward-compatible
 * superset). Uncoded orders are skipped; unknown codes are still recorded
 * with a null campaign in the report. Idempotent via processed_events plus
 * the unique order_id on the attribution row.
 */
@Component
@ConditionalOnProperty(name = "marketing.eca.enabled", havingValue = "true")
public class OrderPlacedConsumer {

    private final AttributionService attribution;
    private final ProcessedEventStore processedEvents;
    private final ObjectMapper json = new ObjectMapper();

    public OrderPlacedConsumer(AttributionService attribution,
            ProcessedEventStore processedEvents) {
        this.attribution = attribution;
        this.processedEvents = processedEvents;
    }

    @KafkaListener(id = "marketing-attribution",
            topics = "order.order.placed.v1", properties = {"auto.offset.reset=earliest"})
    public void onOrderPlaced(ConsumerRecord<String, String> record) {
        String eventId = "%s:%d:%d".formatted(record.topic(), record.partition(),
            record.offset());
        if (!processedEvents.recordIfAbsent(eventId)) {
            return;
        }
        JsonNode event = json.readTree(record.value());
        attribution.onOrderPlaced(event.path("orderId").asText(),
            event.has("trackingCode") ? event.path("trackingCode").asText(null) : null,
            new java.math.BigDecimal(event.path("grandTotal").asText("0")),
            event.has("placedAt") && !event.path("placedAt").isNull()
                ? Instant.parse(event.path("placedAt").asText()) : Instant.now());
    }
}
