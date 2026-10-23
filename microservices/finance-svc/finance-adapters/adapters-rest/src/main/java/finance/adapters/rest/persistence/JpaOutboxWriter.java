package finance.adapters.rest.persistence;

import org.springframework.stereotype.Repository;
import finance.application.OutboxWriter;

/** Outbox rows land in the caller's transaction (ADR-009: no dual writes). */
@Repository
public class JpaOutboxWriter implements OutboxWriter {

    private final OutboxRepository outbox;

    public JpaOutboxWriter(OutboxRepository outbox) {
        this.outbox = outbox;
    }

    @Override
    public void append(String eventType, String aggregateKey, String payloadJson) {
        outbox.save(new OutboxEntryEntity(eventType, aggregateKey, payloadJson));
    }
}
