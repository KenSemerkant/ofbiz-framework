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

            @Override
            public PartyPage findByRole(String roleTypeId, int page, int size) {
                return new PartyPage(List.of(), page, size, 0);
            }

            @Override
            public PartyPage allPaged(int page, int size) {
                // P9-F4: pages over the in-memory map, ordered by id
                var ids = storeData.keySet().stream().sorted().toList();
                var items = ids.stream()
                    .map(id -> new PartyPage.PartySummary(id,
                        storeData.get(id).kind().name(), "test"))
                    .toList();
                return new PartyPage(items, page, size, items.size());
            }
        },
        (eventType, aggregateKey, payload) -> outboxEvents.add(eventType),
        party -> "{\"partyId\":\"" + party.partyId() + "\"}");

    /** P9-F4: all-parties listing delegates to the store paging. */
    @Test
    void allPagedListsEveryParty() {
        service.upsert(Party.person("P1",
            new party.domain.Person("Ada", "Lovelace")));
        service.upsert(Party.person("P2",
            new party.domain.Person("Grace", "Hopper")));
        PartyPage page = service.allPaged(0, 20);
        assertEquals(2, page.totalElements());
        assertEquals(java.util.List.of("P1", "P2"),
            page.items().stream().map(PartyPage.PartySummary::partyId).toList());
    }

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
