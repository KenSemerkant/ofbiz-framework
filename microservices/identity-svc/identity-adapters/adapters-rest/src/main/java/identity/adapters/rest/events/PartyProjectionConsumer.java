package identity.adapters.rest.events;

import identity.application.PartyProjectionService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * P1-F1-S3: consumes `party.party.*` events into the local projection.
 * Idempotent by event_id — a restart or full topic replay produces zero
 * duplicate rows (proven in PartyProjectionConsumerTest).
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
            properties = {"auto.offset.reset=earliest"})
    public void onPartyEvent(String payload) {
        JsonNode event = json.readTree(payload);
        String eventId = event.path("event_id").asText(null);
        String partyId = event.path("party_id").asText(null);
        if (eventId == null || eventId.isBlank() || partyId == null || partyId.isBlank()) {
            return; // not a party event we can project — skip, do not fail the batch
        }
        projection.apply(eventId, partyId, payload);
    }
}
