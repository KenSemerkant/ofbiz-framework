package party.adapters.rest.events;

import java.util.HashSet;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import party.application.ReverseSyncService;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * P2-F2-S3: consumes party-svc's own party.party.created/updated.v1 events
 * and writes them back to the monolith tables (new-service wins).
 */
@Component
@ConditionalOnProperty(name = "party.monolith.sync.enabled", havingValue = "true")
public class ReverseSyncConsumer {

    private final ReverseSyncService reverseSync;
    private final ObjectMapper json = new ObjectMapper();

    public ReverseSyncConsumer(ReverseSyncService reverseSync) {
        this.reverseSync = reverseSync;
    }

    @KafkaListener(id = "party-reverse-sync",
            topics = {"party.party.created.v1", "party.party.updated.v1"},
            properties = {"auto.offset.reset=earliest"})
    public void onPartyEvent(String payload) {
        JsonNode event = json.readTree(payload);
        Set<String> roles = new HashSet<>();
        event.path("roles").forEach(role -> roles.add(role.asText()));
        reverseSync.onPartyEvent(new ReverseSyncService.PartySnapshot(
            event.path("partyId").asText(),
            event.path("kind").asText(),
            event.path("firstName").asText(null),
            event.path("lastName").asText(null),
            event.path("groupName").asText(null),
            roles));
    }
}
