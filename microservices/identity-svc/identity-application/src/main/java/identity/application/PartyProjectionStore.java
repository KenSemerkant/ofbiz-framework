package identity.application;

import identity.domain.PartyProjection;
import java.util.Optional;

/** Persistence port for the party read model + idempotency marker (P1-F1-S3). */
public interface PartyProjectionStore {

    boolean alreadyProcessed(String eventId);

    void upsert(PartyProjection projection, String eventId);

    Optional<PartyProjection> find(String partyId);
}
