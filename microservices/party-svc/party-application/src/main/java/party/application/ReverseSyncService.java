package party.application;

import java.util.Set;

/**
 * P2-F2-S3: applies `party.party.created/updated.v1` back to the monolith.
 * Conflict policy: new-service wins (ADR-012) — the event payload is the
 * source of truth and overwrites the monolith row. Role events
 * (party.role.granted/revoked) need no separate handling: every
 * created/updated payload carries the full role set, so the set-sync in the
 * writer converges roles between events.
 */
public class ReverseSyncService {

    private final MonolithSyncWriter monolith;

    public ReverseSyncService(MonolithSyncWriter monolith) {
        this.monolith = monolith;
    }

    public record PartySnapshot(String partyId, String kind, String firstName,
            String lastName, String groupName, Set<String> roles) {
    }

    public void onPartyEvent(PartySnapshot snapshot) {
        monolith.syncParty(snapshot.partyId(), snapshot.kind(), snapshot.firstName(),
            snapshot.lastName(), snapshot.groupName(), snapshot.roles());
    }
}
