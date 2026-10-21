package party.adapters.rest.persistence;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;
import party.application.MonolithProjectionSink;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * P2-F2-S2: parses the Debezium envelope and mirrors the change into the
 * monolith projection tables. Party delete (op=d) cascades roles; person/
 * group rows without an existing party projection are created so out-of-order
 * table streams converge.
 */
@Repository
public class JpaMonolithProjectionSink implements MonolithProjectionSink {

    private final MonolithPartyRepository parties;
    private final ProcessedEventRepository processedEvents;
    private final TransactionTemplate transactions;
    private final ObjectMapper json = new ObjectMapper();

    public JpaMonolithProjectionSink(MonolithPartyRepository parties,
            ProcessedEventRepository processedEvents, TransactionTemplate transactions) {
        this.parties = parties;
        this.processedEvents = processedEvents;
        this.transactions = transactions;
    }

    @Override
    public boolean alreadyProcessed(String eventId) {
        return processedEvents.existsById(eventId);
    }

    @Override
    public void apply(String eventId, String envelopeJson) {
        transactions.executeWithoutResult(tx -> {
            JsonNode event = json.readTree(envelopeJson);
            String table = event.path("source").path("table").asText("");
            String op = event.path("op").asText("c");
            JsonNode row = op.equals("d") ? event.path("before") : event.path("after");

            switch (table) {
                case "PARTY" -> applyParty(op, row);
                case "PERSON" -> setNames(op, row, false);
                case "PARTY_GROUP" -> setNames(op, row, true);
                case "PARTY_ROLE" -> applyRole(op, row);
                default -> { /* not a party-slice table — skip */ }
            }
            processedEvents.save(new ProcessedEventEntity(eventId));
        });
    }

    private void applyParty(String op, JsonNode row) {
        String partyId = row.path("PARTY_ID").asText();
        if (op.equals("d")) {
            parties.deleteById(partyId);
            return;
        }
        MonolithPartyEntity entity = parties.findById(partyId)
            .orElseGet(() -> new MonolithPartyEntity(partyId));
        entity.setPartyTypeId(row.path("PARTY_TYPE_ID").asText(entity.getPartyTypeId()));
        parties.save(entity);
    }

    private void setNames(String op, JsonNode row, boolean group) {
        if (op.equals("d")) {
            return; // the PARTY delete carries removal
        }
        String partyId = row.path("PARTY_ID").asText();
        MonolithPartyEntity entity = parties.findById(partyId)
            .orElseGet(() -> new MonolithPartyEntity(partyId));
        if (group) {
            entity.setGroupName(row.path("GROUP_NAME").asText(null));
        } else {
            entity.setFirstName(row.path("FIRST_NAME").asText(null));
            entity.setLastName(row.path("LAST_NAME").asText(null));
        }
        parties.save(entity);
    }

    private void applyRole(String op, JsonNode row) {
        String partyId = row.path("PARTY_ID").asText();
        String role = row.path("ROLE_TYPE_ID").asText();
        MonolithPartyEntity entity = parties.findById(partyId).orElse(null);
        if (entity == null) {
            return; // role for an unprojected party — party row will follow
        }
        if (op.equals("d")) {
            entity.getRoles().remove(role);
        } else {
            entity.getRoles().add(role);
        }
        parties.save(entity);
    }
}
