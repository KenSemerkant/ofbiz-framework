package party.adapters.rest.events;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import party.application.MonolithProjectionService;

/**
 * P2-F2-S2: consumes the Debezium CDC topics of the monolith party slice
 * into the local projection. The event id is topic:partition:offset — a
 * rebalance redelivery or full re-consume applies each event exactly once.
 */
@Component
@ConditionalOnProperty(name = "party.cdc.enabled", havingValue = "true",
        matchIfMissing = true)
public class MonolithPartyCdcConsumer {

    private final MonolithProjectionService projection;

    public MonolithPartyCdcConsumer(MonolithProjectionService projection) {
        this.projection = projection;
    }

    @KafkaListener(id = "party-monolith-projection",
            topics = {"ofbiz.PARTY", "ofbiz.PERSON", "ofbiz.PARTY_GROUP", "ofbiz.PARTY_ROLE"},
            properties = {"auto.offset.reset=earliest"})
    public void onCdcEvent(ConsumerRecord<String, String> record) {
        String eventId = "%s:%d:%d".formatted(record.topic(), record.partition(), record.offset());
        projection.apply(eventId, record.value());
    }
}
