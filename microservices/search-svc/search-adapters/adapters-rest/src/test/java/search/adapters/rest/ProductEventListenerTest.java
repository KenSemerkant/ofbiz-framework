package search.adapters.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import search.application.CategoryNames;
import search.application.ProcessedEvents;
import search.application.ProductIndexService;
import search.domain.SearchIndex;

/**
 * P3-F3-S1: the listener maps topic -> use case and parses the exact
 * payload shapes product-svc's serializer emits. Malformed payloads must
 * throw so the shared retry-then-DLQ handler takes over.
 */
class ProductEventListenerTest {

    static final class RecordingIndex implements SearchIndex {
        search.domain.SearchDocument lastIndexed;
        String deleted;
        String got;
        final Map<String, Map<String, String>> docs = new java.util.HashMap<>();

        @Override
        public void index(search.domain.SearchDocument document) {
            lastIndexed = document;
            docs.put(document.docId(), new java.util.HashMap<>(document.fields()));
        }

        @Override
        public List<search.domain.SearchResult> search(search.domain.SearchQuery query) {
            return List.of();
        }

        @Override
        public void recreate(String index) {
        }

        @Override
        public void delete(String index, String docId) {
            deleted = docId;
            docs.remove(docId);
        }

        @Override
        public Map<String, String> get(String index, String docId) {
            got = docId;
            return docs.getOrDefault(docId, Map.of());
        }
    }

    static final class RecordingCategories implements CategoryNames {
        String savedId;
        String savedName;

        @Override
        public void save(String categoryId, String name) {
            savedId = categoryId;
            savedName = name;
        }

        @Override
        public Map<String, String> resolve(java.util.Collection<String> categoryIds) {
            return Map.of();
        }
    }

    static final class RecordingProcessed implements ProcessedEvents {
        final Set<String> recorded = new HashSet<>();

        @Override
        public boolean seen(String eventId) {
            return recorded.contains(eventId);
        }

        @Override
        public void record(String eventId) {
            recorded.add(eventId);
        }
    }

    private static ProductEventListener listener(RecordingIndex index,
            RecordingCategories categories, RecordingProcessed processed) {
        return new ProductEventListener(
            new ProductIndexService(index, processed, categories));
    }

    private static ConsumerRecord<String, String> record(String topic, String payload) {
        return new ConsumerRecord<>(topic, 3, 7, "key", payload);
    }

    @Test
    void createdPayloadMapsToSnapshot() {
        RecordingIndex index = new RecordingIndex();
        listener(index, new RecordingCategories(), new RecordingProcessed())
            .onProductEvent(record(ProductEventListener.PRODUCT_CREATED, """
                {"productId":"P1","productTypeId":"FINISHED_GOOD","internalName":"LAP-1",
                 "productName":"Thinkpad Laptop","categories":["CAT-LAP"],
                 "associations":[],
                 "prices":[{"productPriceTypeId":"DEFAULT_PRICE","currencyUomId":"USD",
                   "fromDate":"2026-01-01T00:00:00Z","thruDate":null,"price":1299.99}]}
                """));

        assertEquals("P1", index.lastIndexed.docId());
        assertEquals(ProductIndexService.INDEX, index.lastIndexed.index());
        assertEquals("Thinkpad Laptop", index.lastIndexed.fields().get("productName"));
        assertEquals("1299.99", index.lastIndexed.fields().get("defaultPrice"));
        assertEquals("CAT-LAP", index.lastIndexed.fields().get("categoryIds"));
    }

    /**
     * Fixtures below are the exact bytes product-svc's JacksonProductSerializer
     * (Jackson 3 / tools.jackson) emits — captured 2026-10-22 from trunk
     * b360ab861e via a throwaway run, pinned verbatim. This is the contract
     * proof that our Jackson 2 listener parses real producer output, and the
     * canary for formatting drift (BigDecimal renders plain, Instant renders
     * ISO-8601): if product-svc changes either, these assertions fail.
     */
    static final String CAPTURED_PRODUCT_EVENT =
        "{\"productId\":\"P1\",\"productTypeId\":\"FINISHED_GOOD\",\"internalName\":\"LAP-1\","
            + "\"productName\":\"Thinkpad Laptop\",\"categories\":[\"CAT-LAP\"],"
            + "\"associations\":[],\"prices\":[{\"productPriceTypeId\":\"DEFAULT_PRICE\","
            + "\"currencyUomId\":\"USD\",\"fromDate\":\"2026-01-01T00:00:00Z\","
            + "\"thruDate\":null,\"price\":1299.99}]}";

    static final String CAPTURED_PRICE_EVENT =
        "{\"productId\":\"P1\",\"productPriceTypeId\":\"DEFAULT_PRICE\","
            + "\"currencyUomId\":\"USD\",\"price\":1099.99,\"removed\":false}";

    @Test
    void capturedSerializerOutputParsesVerbatim() {
        RecordingIndex index = new RecordingIndex();
        RecordingProcessed processed = new RecordingProcessed();
        ProductIndexService service =
            new ProductIndexService(index, processed, new RecordingCategories());
        ProductEventListener listener = new ProductEventListener(service);

        listener.onProductEvent(
            record(ProductEventListener.PRODUCT_CREATED, CAPTURED_PRODUCT_EVENT));

        assertEquals("P1", index.lastIndexed.docId());
        assertEquals("1299.99", index.lastIndexed.fields().get("defaultPrice"),
            "BigDecimal must render plain (no scientific notation / scale noise)");
        assertEquals("USD", index.lastIndexed.fields().get("defaultPriceCurrency"));

        listener.onProductEvent(
            record(ProductEventListener.PRICE_CHANGED, CAPTURED_PRICE_EVENT));

        assertEquals("1099.99", index.lastIndexed.fields().get("defaultPrice"),
            "captured price-change payload must merge into the default price");
    }

    @Test
    void eventIdBindsToTopicPartitionOffset() {
        RecordingProcessed processed = new RecordingProcessed();
        listener(new RecordingIndex(), new RecordingCategories(), processed)
            .onProductEvent(record(ProductEventListener.PRODUCT_UPDATED,
                "{\"productId\":\"P1\",\"categories\":[],\"associations\":[],\"prices\":[]}"));

        assertEquals(Set.of("product.product.updated.v1:3:7"), processed.recorded);
    }

    @Test
    void deletedPayloadCarriesOnlyTheId() {
        RecordingIndex index = new RecordingIndex();
        listener(index, new RecordingCategories(), new RecordingProcessed())
            .onProductEvent(record(ProductEventListener.PRODUCT_DELETED,
                "{\"productId\":\"P1\"}"));

        assertEquals("P1", index.deleted);
    }

    @Test
    void priceChangePayloadMapsThrough() {
        RecordingIndex index = new RecordingIndex();
        // give the product a doc so the merge path applies
        RecordingProcessed processed = new RecordingProcessed();
        ProductIndexService service =
            new ProductIndexService(index, processed, new RecordingCategories());
        service.upserted("seed", new ProductIndexService.ProductSnapshot("P1", null, null, "N",
            List.of(), List.of(new ProductIndexService.Price("DEFAULT_PRICE", "USD", "10.00"))));
        new ProductEventListener(service).onProductEvent(
            record(ProductEventListener.PRICE_CHANGED, """
                {"productId":"P1","productPriceTypeId":"DEFAULT_PRICE",
                 "currencyUomId":"USD","price":1099.99,"removed":false}
                """));

        assertEquals("1099.99", index.lastIndexed.fields().get("defaultPrice"));
    }

    @Test
    void categoryPayloadFeedsTheProjection() {
        RecordingCategories categories = new RecordingCategories();
        listener(new RecordingIndex(), categories, new RecordingProcessed())
            .onProductEvent(record(ProductEventListener.CATEGORY_UPDATED, """
                {"productCategoryId":"CAT-LAP","categoryName":"Laptops",
                 "primaryParentCategoryId":null}
                """));

        assertEquals("CAT-LAP", categories.savedId);
        assertEquals("Laptops", categories.savedName);
    }

    @Test
    void missingOptionalFieldsAreNullNotStrings() {
        var snapshot = ProductEventListener.snapshot(
            new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(
                Map.of("productId", "P1", "categories", List.of(), "prices", List.of())));

        assertEquals("P1", snapshot.productId());
        assertNull(snapshot.internalName());
        assertNull(snapshot.productName());
        assertTrue(snapshot.categoryIds().isEmpty());
    }

    @Test
    void malformedPayloadThrowsForTheDlqPath() {
        assertThrows(IllegalArgumentException.class,
            () -> listener(new RecordingIndex(), new RecordingCategories(),
                new RecordingProcessed())
                .onProductEvent(record(ProductEventListener.PRODUCT_CREATED, "not-json")));
    }
}
