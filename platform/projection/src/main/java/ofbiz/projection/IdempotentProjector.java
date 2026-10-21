package ofbiz.projection;

/**
 * P2-F2-S2: reusable CDC projection core (ADR-012). Applies an event exactly
 * once per event id; replaying a topic (consumer restart, rebuild from
 * scratch after clearing the projection tables) yields zero duplicates.
 * The sink records the event id in the SAME transaction as the projection
 * write — that pairing is the caller's contract, enforced by the tests.
 */
public final class IdempotentProjector {

    /** Persistence contract: apply() must record eventId atomically. */
    public interface Sink {
        boolean alreadyProcessed(String eventId);

        void apply(String eventId, String payload);
    }

    private final Sink sink;

    public IdempotentProjector(Sink sink) {
        this.sink = sink;
    }

    /** @return true if the event was applied now, false if already seen. */
    public boolean project(String eventId, String payload) {
        if (eventId == null || eventId.isBlank()) {
            throw new IllegalArgumentException("eventId must not be blank");
        }
        if (sink.alreadyProcessed(eventId)) {
            return false;
        }
        sink.apply(eventId, payload);
        return true;
    }
}
