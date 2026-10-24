package manufacturing.adapters.rest.events;

import java.math.BigDecimal;
import java.time.Instant;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import manufacturing.application.MrpService;
import manufacturing.application.ProcessedEventStore;
import manufacturing.domain.MrpEvent;
import manufacturing.domain.MrpEventType;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * P6-T1 demand leg: order.order.placed.v1 → one DEMAND ledger row per
 * order line (payload lines[].productId/quantity per
 * contracts/order/v1/OrderPlaced.avsc; the line array rides the same
 * envelope). Idempotent via processed_events + the mrp_events PK, all in
 * one transaction — a redelivery appends nothing.
 */
@Component
@ConditionalOnProperty(name = "manufacturing.mrp.enabled", havingValue = "true")
public class OrderDemandConsumer {

    private final MrpService mrp;
    private final ProcessedEventStore processedEvents;
    private final ObjectMapper json = new ObjectMapper();

    public OrderDemandConsumer(MrpService mrp, ProcessedEventStore processedEvents) {
        this.mrp = mrp;
        this.processedEvents = processedEvents;
    }

    @KafkaListener(id = "manufacturing-mrp-demand", topics = "order.order.placed.v1",
            properties = {"auto.offset.reset=earliest"})
    @Transactional
    public void onOrderPlaced(ConsumerRecord<String, String> record) {
        String eventId = "%s:%d:%d".formatted(record.topic(), record.partition(),
            record.offset());
        if (!processedEvents.recordIfAbsent(eventId)) {
            return;
        }
        JsonNode event = json.readTree(record.value());
        Instant occurredAt = event.hasNonNull("placedAt") && !event.path("placedAt").asString().isBlank()
            ? Instant.parse(event.path("placedAt").asString())
            : Instant.ofEpochMilli(record.timestamp());
        int line = 0;
        for (JsonNode node : event.path("lines")) {
            mrp.recordEvent(new MrpEvent(eventId + ":" + line++, node.path("productId").asString(),
                MrpEventType.DEMAND, new BigDecimal(node.path("quantity").asString("0")),
                occurredAt));
        }
    }
}
