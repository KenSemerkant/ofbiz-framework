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
 * P6-T1 supply leg: inventory.stock.received/adjusted.v1 → one SUPPLY
 * ledger row per record (payload shape per inventory-svc
 * serializeStockChange: item fields + quantity delta; a negative
 * adjustment nets against supply). Idempotent via processed_events +
 * the mrp_events PK, all in one transaction.
 */
@Component
@ConditionalOnProperty(name = "manufacturing.mrp.enabled", havingValue = "true")
public class InventorySupplyConsumer {

    private final MrpService mrp;
    private final ProcessedEventStore processedEvents;
    private final ObjectMapper json = new ObjectMapper();

    public InventorySupplyConsumer(MrpService mrp, ProcessedEventStore processedEvents) {
        this.mrp = mrp;
        this.processedEvents = processedEvents;
    }

    @KafkaListener(id = "manufacturing-mrp-supply",
            topics = {"inventory.stock.received.v1", "inventory.stock.adjusted.v1"},
            properties = {"auto.offset.reset=earliest"})
    @Transactional
    public void onStockEvent(ConsumerRecord<String, String> record) {
        String eventId = "%s:%d:%d".formatted(record.topic(), record.partition(),
            record.offset());
        if (!processedEvents.recordIfAbsent(eventId)) {
            return;
        }
        JsonNode event = json.readTree(record.value());
        mrp.recordEvent(new MrpEvent(eventId, event.path("productId").asString(),
            MrpEventType.SUPPLY, new BigDecimal(event.path("quantity").asString("0")),
            Instant.ofEpochMilli(record.timestamp())));
    }
}
