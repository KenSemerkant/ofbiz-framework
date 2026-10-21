package party.application;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import party.domain.Party;
import party.domain.PartyGroup;
import party.domain.Person;

/** AC: outbox emits party.party.created/updated and role deltas. */
class PartyServiceTest {

    private final Map<String, Party> storeData = new HashMap<>();
    private final List<String> outboxEvents = new ArrayList<>();

    private final PartyService service = new PartyService(
        new PartyStore() {
            @Override
            public Optional<Party> find(String partyId) {
                return Optional.ofNullable(storeData.get(partyId));
            }

            @Override
            public void save(Party party) {
                storeData.put(party.partyId(), party);
            }
        },
        (eventType, aggregateKey, payload) -> outboxEvents.add(eventType),
        party -> "{\"partyId\":\"" + party.partyId() + "\"}");

    @Test
    void firstUpsertEmitsCreated() {
        service.upsert(Party.person("P1", new Person("Ada", "Lovelace")));
        assertEquals(List.of(PartyService.PARTY_CREATED), outboxEvents);
        assertEquals("Ada", service.get("P1").orElseThrow().person().firstName());
    }

    @Test
    void secondUpsertEmitsUpdated() {
        service.upsert(Party.person("P1", new Person("Ada", "Lovelace")));
        outboxEvents.clear();
        service.upsert(Party.person("P1", new Person("Ada", "King")));
        assertEquals(List.of(PartyService.PARTY_UPDATED), outboxEvents);
    }

    @Test
    void roleDeltasEmittedOnUpdate() {
        Party initial = Party.group("G1", new PartyGroup("Acme"));
        initial.grantRole("CUSTOMER");
        initial.grantRole("SUPPLIER");
        service.upsert(initial);
        outboxEvents.clear();

        Party updated = Party.group("G1", new PartyGroup("Acme"));
        updated.grantRole("CUSTOMER");
        updated.grantRole("DISTRIBUTOR");
        service.upsert(updated);

        // order: updated, granted DISTRIBUTOR, revoked SUPPLIER
        assertEquals(List.of(
            PartyService.PARTY_UPDATED,
            PartyService.ROLE_GRANTED,
            PartyService.ROLE_REVOKED), outboxEvents);
    }

    @Test
    void getUnknownPartyIsEmpty() {
        assertEquals(Optional.empty(), service.get("nope"));
    }
}
