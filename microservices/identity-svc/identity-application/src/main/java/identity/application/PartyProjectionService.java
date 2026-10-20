package identity.application;

import identity.domain.PartyProjection;
import java.time.Instant;

/**
 * P1-F1-S3: projects `party.party.*` events into the local read model.
 * Replay-safe: events already applied (by event_id) are skipped, so a
 * consumer restart or topic replay yields zero duplicates.
 */
public class PartyProjectionService {

    private final PartyProjectionStore store;

    public PartyProjectionService(PartyProjectionStore store) {
        this.store = store;
    }

    public void apply(String eventId, String partyId, String payloadJson) {
        if (store.alreadyProcessed(eventId)) {
            return;
        }
        store.upsert(new PartyProjection(partyId, payloadJson, Instant.now()), eventId);
    }
}
