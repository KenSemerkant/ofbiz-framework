package party.application;

import ofbiz.projection.IdempotentProjector;

/**
 * P2-F2-S2: projects monolith CDC events (ofbiz.PARTY / ofbiz.PERSON /
 * ofbiz.PARTY_GROUP / ofbiz.PARTY_ROLE) into the local monolith projection
 * tables. Idempotent per event id (topic:partition:offset), so clearing the
 * projection tables and re-consuming rebuilds from scratch with zero
 * duplicates.
 */
public class MonolithProjectionService {

    private final IdempotentProjector projector;

    public MonolithProjectionService(IdempotentProjector.Sink sink) {
        this.projector = new IdempotentProjector(sink);
    }

    /** @return true when the event was applied now (false = replay skip). */
    public boolean apply(String eventId, String debeziumEnvelopeJson) {
        return projector.project(eventId, debeziumEnvelopeJson);
    }
}
