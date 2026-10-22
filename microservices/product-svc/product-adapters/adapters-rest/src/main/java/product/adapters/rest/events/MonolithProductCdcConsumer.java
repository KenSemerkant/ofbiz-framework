package product.adapters.rest.events;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import product.application.MonolithProjectionService;

/**
 * P3-GATE: consumes the Debezium CDC topics of the monolith product slice
 * into the local projection. The event id is topic:partition:offset — a
 * rebalance redelivery or full re-consume applies each event exactly once.
 */
@Component
@ConditionalOnProperty(name = "product.cdc.enabled", havingValue = "true",
        matchIfMissing = true)
public class MonolithProductCdcConsumer {

    private final MonolithProjectionService projection;

    public MonolithProductCdcConsumer(MonolithProjectionService projection) {
        this.projection = projection;
    }

    @KafkaListener(id = "product-monolith-projection",
            topics = {"ofbiz.PRODUCT", "ofbiz.PRODUCT_CATEGORY",
                    "ofbiz.PRODUCT_CATEGORY_MEMBER", "ofbiz.PRODUCT_PRICE",
                    "ofbiz.PRODUCT_ASSOC"},
            properties = {"auto.offset.reset=earliest"})
    public void onCdcEvent(ConsumerRecord<String, String> record) {
        String eventId = "%s:%d:%d".formatted(record.topic(), record.partition(), record.offset());
        projection.apply(eventId, record.value());
    }
}
