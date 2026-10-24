package manufacturing.application;

/** Consumer idempotency (ADR-009): event ids already applied are skipped. */
public interface ProcessedEventStore {

    /** @return false when the id was already recorded. */
    boolean recordIfAbsent(String eventId);
}
