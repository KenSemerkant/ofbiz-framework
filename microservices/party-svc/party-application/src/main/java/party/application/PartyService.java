package party.application;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import party.domain.Party;

/**
 * P2-F1-S1 use case: upsert/fetch through the aggregate, outbox events in the
 * same logical transaction. First write emits `party.party.created.v1`,
 * later writes `party.party.updated.v1`; role deltas additionally emit
 * `party.role.granted.v1` / `party.role.revoked.v1` (FUTURESTATE §5.1).
 */
public class PartyService {

    public static final String PARTY_CREATED = "party.party.created.v1";
    public static final String PARTY_UPDATED = "party.party.updated.v1";
    public static final String ROLE_GRANTED = "party.role.granted.v1";
    public static final String ROLE_REVOKED = "party.role.revoked.v1";

    private final PartyStore store;
    private final OutboxWriter outbox;
    private final PartySerializer serializer;

    public PartyService(PartyStore store, OutboxWriter outbox, PartySerializer serializer) {
        this.store = store;
        this.outbox = outbox;
        this.serializer = serializer;
    }

    public Optional<Party> get(String partyId) {
        return store.find(partyId);
    }

    /** P2-F1-S3: paged role lookup. */
    public PartyPage findByRole(String roleTypeId, int page, int size) {
        return store.findByRole(roleTypeId, page, size);
    }

    public void upsert(Party party) {
        Optional<Party> existing = store.find(party.partyId());
        store.save(party);
        outbox.append(existing.isEmpty() ? PARTY_CREATED : PARTY_UPDATED,
            party.partyId(), serializer.serialize(party));
        emitRoleDeltas(existing.orElse(null), party);
    }

    private void emitRoleDeltas(Party before, Party after) {
        List<String> granted = new ArrayList<>(after.roles());
        List<String> revoked = new ArrayList<>();
        if (before != null) {
            granted.removeAll(before.roles());
            revoked.addAll(before.roles());
            revoked.removeAll(after.roles());
        }
        granted.forEach(
            role -> outbox.append(ROLE_GRANTED, after.partyId(), rolePayload(after.partyId(), role)));
        revoked.forEach(
            role -> outbox.append(ROLE_REVOKED, after.partyId(), rolePayload(after.partyId(), role)));
    }

    private String rolePayload(String partyId, String role) {
        return "{\"partyId\":\"" + partyId + "\",\"roleTypeId\":\"" + role + "\"}";
    }
}
