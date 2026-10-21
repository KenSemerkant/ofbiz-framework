package party.application;

import java.util.Set;

/**
 * Reverse-sync port (P2-F2-S3, ADR-012): writes party-svc state back to the
 * monolith tables for not-yet-migrated consumers.
 */
public interface MonolithSyncWriter {

    /**
     * Upsert the party across PARTY / PERSON / PARTY_GROUP / PARTY_ROLE.
     * Conflict policy: the new service wins — every field is overwritten
     * unconditionally; monolith roles not in the event are removed.
     */
    void syncParty(String partyId, String partyTypeId, String firstName, String lastName,
            String groupName, Set<String> roles);
}
