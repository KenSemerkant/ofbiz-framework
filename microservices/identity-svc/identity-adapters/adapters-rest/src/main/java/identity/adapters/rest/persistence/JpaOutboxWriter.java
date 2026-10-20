package identity.adapters.rest.persistence;

import identity.application.OutboxWriter;
import org.springframework.stereotype.Repository;

@Repository
public class JpaOutboxWriter implements OutboxWriter {

    private final OutboxRepository outbox;

    public JpaOutboxWriter(OutboxRepository outbox) {
        this.outbox = outbox;
    }

    @Override
    public void append(String eventType, String payloadJson) {
        outbox.save(new OutboxEntryEntity(eventType, payloadJson));
    }
}
