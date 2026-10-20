package identity.adapters.rest.events;

import identity.adapters.rest.persistence.OutboxEntryEntity;
import identity.adapters.rest.persistence.OutboxRepository;
import java.time.Instant;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Publishes outbox rows to Kafka (ADR-009: relay step — no dual writes).
 * Disabled in unit tests via identity.relay.enabled=false.
 */
@Component
@ConditionalOnProperty(name = "identity.relay.enabled", havingValue = "true")
public class OutboxRelay {

    static final String AUDIT_TOPIC = "audit.auditevent.recorded.v1";

    private final OutboxRepository outbox;
    private final KafkaTemplate<String, String> kafka;
    private final TransactionTemplate transactions;

    public OutboxRelay(OutboxRepository outbox, KafkaTemplate<String, String> kafka,
            TransactionTemplate transactions) {
        this.outbox = outbox;
        this.kafka = kafka;
        this.transactions = transactions;
    }

    @Scheduled(fixedDelay = 1000)
    public void publishPending() {
        for (OutboxEntryEntity entry : outbox.findUnpublished()) {
            kafka.send(AUDIT_TOPIC, entry.getEventType(), entry.getPayload()).join();
            transactions.executeWithoutResult(tx -> outbox.findById(entry.getId())
                .ifPresent(e -> e.markPublished(Instant.now())));
        }
    }
}
