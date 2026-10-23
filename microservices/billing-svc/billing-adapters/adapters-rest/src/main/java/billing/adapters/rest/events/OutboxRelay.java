package billing.adapters.rest.events;

import java.time.Instant;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import billing.adapters.rest.persistence.OutboxEntryEntity;
import billing.adapters.rest.persistence.OutboxRepository;

/**
 * Publishes outbox rows to Kafka (ADR-009: relay step — no dual writes).
 * Event type = topic per the `<context>.<aggregate>.<action>.vN`
 * convention. Disabled in tests via billing.relay.enabled=false.
 */
@Component
@ConditionalOnProperty(name = "billing.relay.enabled", havingValue = "true")
public class OutboxRelay {

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
            kafka.send(entry.getEventType(), entry.getAggregateKey(), entry.getPayload()).join();
            transactions.executeWithoutResult(tx -> outbox.findById(entry.getId())
                .ifPresent(e -> e.markPublished(Instant.now())));
        }
    }
}
