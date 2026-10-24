package content.adapters.rest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;
import content.adapters.rest.events.OutboxRelay;
import content.adapters.rest.persistence.JacksonContentSerializer;
import content.adapters.rest.persistence.OutboxEntryEntity;
import content.adapters.rest.persistence.OutboxRepository;
import content.domain.Content;
import content.domain.ContentStatus;
import content.domain.ContentType;

/**
 * Unit coverage for the adapter classes the API slice leaves cold (relay,
 * serializer) — the FinanceAdaptersUnitTest pattern: mocks, no containers.
 * S3 delegation is pinned in S3ObjectStoreTest.
 */
class ContentAdaptersUnitTest {

    @Test
    void relayPublishesAndMarks() {
        OutboxEntryEntity entry = new OutboxEntryEntity("content.content.published.v1",
            "C-1", "{}");
        OutboxRepository repo = org.mockito.Mockito.mock(OutboxRepository.class);
        org.mockito.Mockito.when(repo.findUnpublished()).thenReturn(List.of(entry));
        org.mockito.Mockito.when(repo.findById(org.mockito.ArgumentMatchers.any()))
            .thenReturn(java.util.Optional.of(entry));
        @SuppressWarnings("unchecked")
        org.springframework.kafka.core.KafkaTemplate<String, String> kafka =
            org.mockito.Mockito.mock(org.springframework.kafka.core.KafkaTemplate.class);
        org.mockito.Mockito
            .when(kafka.send("content.content.published.v1", "C-1", "{}"))
            .thenReturn(java.util.concurrent.CompletableFuture.completedFuture(null));
        OutboxRelay relay = new OutboxRelay(repo, kafka,
            new TransactionTemplate(new NoopTransactionManager()));
        relay.publishPending();
        org.mockito.Mockito.verify(kafka).send("content.content.published.v1", "C-1", "{}");
        assertNotNull(entry.getPayload());
    }

    @Test
    void serializerCarriesEventContractFields() {
        JacksonContentSerializer serializer = new JacksonContentSerializer();
        Content uploaded = Content.rehydrate("C-S", ContentType.DOCUMENT, "spec.pdf", null,
            ContentStatus.DRAFT, "contents/C-S", "application/pdf", 42L);
        String payload = serializer.serialize(uploaded);
        assertTrue(payload.contains("\"contentId\":\"C-S\""));
        assertTrue(payload.contains("\"contentTypeId\":\"DOCUMENT\""));
        assertTrue(payload.contains("\"statusId\":\"DRAFT\""));
        assertTrue(payload.contains("\"s3Key\":\"contents/C-S\""));
        assertTrue(payload.contains("\"sizeBytes\":42"));
        Content folder = Content.rehydrate("C-F", ContentType.FOLDER, "docs", null,
            ContentStatus.DRAFT, null, null, null);
        assertFalse(serializer.serialize(folder).contains("s3Key"),
            "folders never carry an object reference");
    }

    static class NoopTransactionManager implements PlatformTransactionManager {
        @Override
        public TransactionStatus getTransaction(TransactionDefinition definition) {
            return new SimpleTransactionStatus();
        }

        @Override
        public void commit(TransactionStatus status) {
        }

        @Override
        public void rollback(TransactionStatus status) {
        }
    }
}
