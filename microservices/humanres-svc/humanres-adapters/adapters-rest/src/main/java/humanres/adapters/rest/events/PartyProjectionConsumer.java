package humanres.adapters.rest.events;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import humanres.application.PartyProjectionStore;
import humanres.application.ProcessedEventStore;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * P6-T3 party projection consumer: party.party.created/updated.v1 →
 * party_projection (id + names JSON), so onboarding can prove a party
 * exists without an east-west call (FUTURESTATE §5.10). Idempotent via
 * processed_events; unknown payload fields are ignored, missing name
 * fields are simply absent from the names JSON.
 */
@Component
@ConditionalOnProperty(name = "humanres.projection.enabled", havingValue = "true")
public class PartyProjectionConsumer {

    private final PartyProjectionStore projections;
    private final ProcessedEventStore processedEvents;
    private final ObjectMapper json = new ObjectMapper();

    public PartyProjectionConsumer(PartyProjectionStore projections,
            ProcessedEventStore processedEvents) {
        this.projections = projections;
        this.processedEvents = processedEvents;
    }

    @KafkaListener(id = "humanres-party-projection",
            topics = {"party.party.created.v1", "party.party.updated.v1"},
            properties = {"auto.offset.reset=earliest"})
    public void onPartyEvent(ConsumerRecord<String, String> record) {
        String eventId = "%s:%d:%d".formatted(record.topic(), record.partition(),
            record.offset());
        if (!processedEvents.recordIfAbsent(eventId)) {
            return;
        }
        JsonNode event = json.readTree(record.value());
        String partyId = event.path("partyId").asText();
        if (partyId.isBlank()) {
            return; // nothing to project — no party id is a poison guard, not an error
        }
        ObjectNode names = json.createObjectNode();
        for (String field : new String[] {"firstName", "lastName", "groupName"}) {
            if (event.hasNonNull(field)) {
                names.put(field, event.path(field).asText());
            }
        }
        projections.upsert(partyId, names.toString());
    }
}
