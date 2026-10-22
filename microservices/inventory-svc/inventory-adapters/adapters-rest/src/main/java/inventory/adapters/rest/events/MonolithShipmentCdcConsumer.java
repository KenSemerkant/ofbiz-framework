package inventory.adapters.rest.events;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import inventory.application.MonolithProjectionService;

/**
 * P3-GATE: consumes the Debezium CDC topics of the monolith shipment slice
 * into the local projection. The event id is topic:partition:offset — a
 * rebalance redelivery or full re-consume applies each event exactly once.
 */
@Component
@ConditionalOnProperty(name = "inventory.cdc.enabled", havingValue = "true",
        matchIfMissing = true)
public class MonolithShipmentCdcConsumer {

    private final MonolithProjectionService projection;

    public MonolithShipmentCdcConsumer(MonolithProjectionService projection) {
        this.projection = projection;
    }

    @KafkaListener(id = "inventory-monolith-shipment-projection",
            topics = {"ofbiz.SHIPMENT", "ofbiz.SHIPMENT_ITEM"},
            properties = {"auto.offset.reset=earliest"})
    public void onCdcEvent(ConsumerRecord<String, String> record) {
        String eventId = "%s:%d:%d".formatted(record.topic(), record.partition(), record.offset());
        projection.apply(eventId, record.value());
    }
}
