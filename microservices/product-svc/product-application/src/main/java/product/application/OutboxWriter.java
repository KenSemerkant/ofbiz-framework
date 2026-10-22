package product.application;

/** Transactional outbox port (ADR-009): state changes land in the same tx. */
public interface OutboxWriter {

    void append(String eventType, String aggregateKey, String payloadJson);
}
