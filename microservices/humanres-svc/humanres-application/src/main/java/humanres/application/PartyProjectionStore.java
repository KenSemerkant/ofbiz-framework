package humanres.application;

import java.util.Optional;

/**
 * Local party projection (FUTURESTATE §5.10: party linkage by id only).
 * Fed from party.party.created/updated.v1; the names JSON is display data
 * so onboarding can prove the party exists without an east-west call.
 */
public interface PartyProjectionStore {

    void upsert(String partyId, String namesJson);

    /** @return the names JSON, or empty when the party was never projected. */
    Optional<String> find(String partyId);
}
