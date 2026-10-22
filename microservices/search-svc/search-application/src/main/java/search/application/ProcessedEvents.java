package search.application;

/** Consumer idempotency markers (ADR-009 processed_events table). */
public interface ProcessedEvents {

    boolean seen(String eventId);

    void record(String eventId);
}
