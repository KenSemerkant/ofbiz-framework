package order.adapters.rest.events;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import order.application.AttributionService;
import order.application.InventoryUnavailableException;
import order.application.OrderEcaService;
import order.application.OrderLifecycleService;
import order.application.ProcessedEventStore;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * P4-F2-S1/P4-F2-S2 ECA consumers on order-svc's own events:
 * <ul>
 * <li>order.order.placed.v1 → saga ECA steps (reserve + invoice trigger);
 *     a stock failure compensates and cancels the order (typed event, not
 *     a silent drop)</li>
 * <li>order.order.placed.v1 → marketing attribution (tracking code)</li>
 * <li>order.order.cancelled.v1 → compensation: release live reservations</li>
 * </ul>
 * Idempotent via processed_events + the guarded saga markers.
 */
@Component
@ConditionalOnProperty(name = "order.eca.enabled", havingValue = "true")
public class OrderConsumers {

    private final OrderEcaService eca;
    private final OrderLifecycleService lifecycle;
    private final AttributionService attribution;
    private final ProcessedEventStore processedEvents;
    private final ObjectMapper json = new ObjectMapper();

    public OrderConsumers(OrderEcaService eca, OrderLifecycleService lifecycle,
            AttributionService attribution, ProcessedEventStore processedEvents) {
        this.eca = eca;
        this.lifecycle = lifecycle;
        this.attribution = attribution;
        this.processedEvents = processedEvents;
    }

    @KafkaListener(id = "order-placed-eca",
            topics = "order.order.placed.v1", properties = {"auto.offset.reset=earliest"})
    public void onPlaced(ConsumerRecord<String, String> record) {
        String eventId = "%s:%d:%d".formatted(record.topic(), record.partition(),
            record.offset());
        if (!processedEvents.recordIfAbsent(eventId)) {
            return;
        }
        JsonNode event = json.readTree(record.value());
        String orderId = event.path("orderId").asText();
        try {
            eca.onPlaced(orderId);
        } catch (InventoryUnavailableException e) {
            // P4-F2-S1 compensation on partial failure: the ECA service
            // released what it had reserved; cancel + emit the typed event
            lifecycle.cancel(orderId, "insufficient stock: " + e.getMessage());
        }
    }

    @KafkaListener(id = "order-attribution",
            topics = "order.order.placed.v1", properties = {"auto.offset.reset=earliest"})
    public void onPlacedAttribution(ConsumerRecord<String, String> record) {
        String eventId = "attr:%s:%d:%d".formatted(record.topic(), record.partition(),
            record.offset());
        if (!processedEvents.recordIfAbsent(eventId)) {
            return;
        }
        JsonNode event = json.readTree(record.value());
        attribution.onOrderPlaced(event.path("orderId").asText(),
            event.path("customerId").asText(), event.has("trackingCode")
                ? event.path("trackingCode").asText(null) : null,
            null);
    }

    @KafkaListener(id = "order-cancelled-compensation",
            topics = "order.order.cancelled.v1", properties = {"auto.offset.reset=earliest"})
    public void onCancelled(ConsumerRecord<String, String> record) {
        JsonNode event = json.readTree(record.value());
        eca.compensate(event.path("orderId").asText());
    }
}
