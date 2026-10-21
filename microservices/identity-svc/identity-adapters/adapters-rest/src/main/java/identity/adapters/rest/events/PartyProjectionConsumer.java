package identity.adapters.rest.events;

import identity.application.PartyProjectionService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * P1-F1-S3: consumes `party.party.*` events into the local projection.
 * Idempotent by event id — a restart or full topic replay produces zero
 * duplicate rows (proven in PartyProjectionConsumerTest).
 *
 * <p>P2-F3-S1: party-svc (the new event producer) publishes the aggregate
 * payload with camelCase {@code partyId} and no {@code event_id}; the event
 * id falls back to topic:partition:offset, and both spellings are accepted
 * while P1-era demo events still exist on the topics.
 */
@Component
@ConditionalOnProperty(name = "party.consumer.enabled", havingValue = "true")
public class PartyProjectionConsumer {

    private final PartyProjectionService projection;
    private final ObjectMapper json;

    public PartyProjectionConsumer(PartyProjectionService projection, ObjectMapper json) {
        this.projection = projection;
        this.json = json;
    }

    @KafkaListener(id = "identity-party-projection", topicPattern = "party\\.party\\..*\\.v1",
            properties = {"auto.offset.reset=earliest", "max.partition.fetch.bytes=4194304",
                    "fetch.max.bytes=8388608", "max.poll.records=200"})
    public void onPartyEvent(ConsumerRecord<String, String> record) {
        String payload = record.value();
        JsonNode event = json.readTree(payload);
        String partyId = event.path("partyId").asText(event.path("party_id").asText(null));
        if (partyId == null || partyId.isBlank()) {
            return; // not a party event we can project — skip, do not fail the batch
        }
        // P1 events carry event_id; party-svc events key on the record position
        String payloadEventId = event.path("event_id").asText(null);
        String eventId = payloadEventId != null && !payloadEventId.isBlank()
            ? payloadEventId
            : "%s:%d:%d".formatted(record.topic(), record.partition(), record.offset());
        projection.apply(eventId, partyId, payload);
    }
}
