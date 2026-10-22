package search.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import search.domain.SearchDocument;
import search.domain.SearchIndex;
import search.domain.SearchQuery;
import search.domain.SearchResult;

/**
 * P3-F3-S1: the indexer transform (doc shape, default-price pick, category
 * name resolution) and the replay skip. Idempotency matters because a
 * rebalance redelivers — a skipped event must leave the index untouched.
 */
class ProductIndexServiceTest {

    static final class RecordingIndex implements SearchIndex {
        final List<SearchDocument> indexed = new ArrayList<>();
        final List<String> deleted = new ArrayList<>();
        final Map<String, Map<String, String>> docs = new HashMap<>();
        final Map<String, Map<String, String>> getCalls = new HashMap<>();

        @Override
        public void index(SearchDocument document) {
            indexed.add(document);
            docs.put(document.docId(), new HashMap<>(document.fields()));
        }

        @Override
        public List<SearchResult> search(SearchQuery query) {
            return List.of();
        }

        @Override
        public void recreate(String index) {
        }

        @Override
        public void delete(String index, String docId) {
            deleted.add(docId);
            docs.remove(docId);
        }

        @Override
        public Map<String, String> get(String index, String docId) {
            getCalls.put(docId, null);
            return docs.getOrDefault(docId, Map.of());
        }

        Map<String, String> fields(String docId) {
            return indexed.stream()
                .filter(doc -> doc.docId().equals(docId))
                .reduce((first, second) -> second) // last write wins
                .map(SearchDocument::fields)
                .orElseThrow();
        }
    }

    static final class FakeProcessedEvents implements ProcessedEvents {
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

    static final class FakeCategoryNames implements CategoryNames {
        final Map<String, String> names = new LinkedHashMap<>();

        @Override
        public void save(String categoryId, String name) {
            names.put(categoryId, name);
        }

        @Override
        public Map<String, String> resolve(java.util.Collection<String> categoryIds) {
            Map<String, String> known = new LinkedHashMap<>();
            for (String id : categoryIds) {
                if (names.containsKey(id)) {
                    known.put(id, names.get(id));
                }
            }
            return known;
        }
    }

    static ProductIndexService.ProductSnapshot snapshot(String productId) {
        return new ProductIndexService.ProductSnapshot(productId, "FINISHED_GOOD",
            "LAP-" + productId, "Thinkpad Laptop", List.of("CAT-LAP", "CAT-NEW"),
            List.of(new ProductIndexService.Price("LIST_PRICE", "EUR", "99.00"),
                new ProductIndexService.Price("DEFAULT_PRICE", "USD", "1299.99")));
    }

    @Test
    void upsertBuildsVersionedDocWithResolvedNamesAndDefaultPrice() {
        RecordingIndex index = new RecordingIndex();
        FakeCategoryNames categories = new FakeCategoryNames();
        categories.save("CAT-LAP", "Laptops");
        ProductIndexService service =
            new ProductIndexService(index, new FakeProcessedEvents(), categories);

        assertTrue(service.upserted("product.product.created.v1:0:0", snapshot("P1")));

        Map<String, String> fields = index.fields("P1");
        assertEquals("1", fields.get("schemaVersion"));
        assertEquals("P1", fields.get("productId"));
        assertEquals("FINISHED_GOOD", fields.get("productTypeId"));
        assertEquals("LAP-P1", fields.get("internalName"));
        assertEquals("Thinkpad Laptop", fields.get("productName"));
        assertEquals("CAT-LAP,CAT-NEW", fields.get("categoryIds"));
        // unknown CAT-NEW has no name yet — only known names are indexed
        assertEquals("Laptops", fields.get("categoryNames"));
        // DEFAULT_PRICE wins over the first row (LIST_PRICE)
        assertEquals("1299.99", fields.get("defaultPrice"));
        assertEquals("DEFAULT_PRICE", fields.get("defaultPriceType"));
        assertEquals("USD", fields.get("defaultPriceCurrency"));
        assertEquals(ProductIndexService.INDEX, index.indexed.get(0).index());
    }

    @Test
    void upsertWithoutPricesOmitsPriceFields() {
        RecordingIndex index = new RecordingIndex();
        ProductIndexService service =
            new ProductIndexService(index, new FakeProcessedEvents(), new FakeCategoryNames());
        var noPrices = new ProductIndexService.ProductSnapshot("P2", null, null, "Widget",
            List.of(), List.of());

        service.upserted("product.product.created.v1:0:0", noPrices);

        Map<String, String> fields = index.fields("P2");
        assertFalse(fields.containsKey("defaultPrice"));
        assertFalse(fields.containsKey("productTypeId"));
        assertFalse(fields.containsKey("categoryIds"));
        // falls back to the first price row when no DEFAULT_PRICE exists
        service.upserted("product.product.created.v1:0:1",
            new ProductIndexService.ProductSnapshot("P3", null, null, "Gadget", List.of(),
                List.of(new ProductIndexService.Price("LIST_PRICE", "EUR", "5.00"))));
        assertEquals("5.00", index.fields("P3").get("defaultPrice"));
    }

    @Test
    void replayedEventIdIsSkipped() {
        RecordingIndex index = new RecordingIndex();
        ProductIndexService service =
            new ProductIndexService(index, new FakeProcessedEvents(), new FakeCategoryNames());

        assertTrue(service.upserted("product.product.created.v1:0:0", snapshot("P1")));
        assertFalse(service.upserted("product.product.created.v1:0:0",
            new ProductIndexService.ProductSnapshot("P1", "X", "X", "X", List.of(), List.of())));
        assertEquals(1, index.indexed.size());

        assertFalse(service.deleted("product.product.created.v1:0:0", "P1"));
        assertTrue(index.docs.containsKey("P1"), "replay must not delete the doc");
    }

    @Test
    void deletedRemovesTheDoc() {
        RecordingIndex index = new RecordingIndex();
        ProductIndexService service =
            new ProductIndexService(index, new FakeProcessedEvents(), new FakeCategoryNames());

        assertTrue(service.deleted("product.product.deleted.v1:0:0", "P1"));

        assertEquals(List.of("P1"), index.deleted);
    }

    @Test
    void priceChangeReplacesTheShownDefaultRow() {
        RecordingIndex index = new RecordingIndex();
        ProductIndexService service =
            new ProductIndexService(index, new FakeProcessedEvents(), new FakeCategoryNames());
        service.upserted("product.product.created.v1:0:0", snapshot("P1"));

        assertTrue(service.priceChanged("product.price.changed.v1:0:0", "P1",
            "DEFAULT_PRICE", "USD", "1099.99", false));

        Map<String, String> fields = index.fields("P1");
        assertEquals("1099.99", fields.get("defaultPrice"));
        assertEquals("USD", fields.get("defaultPriceCurrency"));
        // the rest of the doc survives the merge
        assertEquals("Thinkpad Laptop", fields.get("productName"));
    }

    @Test
    void priceChangeIgnoresRowsTheDocDoesNotShow() {
        RecordingIndex index = new RecordingIndex();
        ProductIndexService service =
            new ProductIndexService(index, new FakeProcessedEvents(), new FakeCategoryNames());
        service.upserted("product.product.created.v1:0:0", snapshot("P1"));
        int indexedBefore = index.indexed.size();

        // LIST_PRICE row changed — not the indexed default, no reindex
        assertTrue(service.priceChanged("product.price.changed.v1:0:0", "P1",
            "LIST_PRICE", "EUR", "1.00", false));
        assertEquals(indexedBefore, index.indexed.size());
        assertEquals("1299.99", index.fields("P1").get("defaultPrice"));
    }

    @Test
    void removedDefaultPriceClearsTheFields() {
        RecordingIndex index = new RecordingIndex();
        ProductIndexService service =
            new ProductIndexService(index, new FakeProcessedEvents(), new FakeCategoryNames());
        service.upserted("product.product.created.v1:0:0", snapshot("P1"));

        assertTrue(service.priceChanged("product.price.changed.v1:0:0", "P1",
            "DEFAULT_PRICE", "USD", "1299.99", true));

        Map<String, String> fields = index.fields("P1");
        assertFalse(fields.containsKey("defaultPrice"));
        assertFalse(fields.containsKey("defaultPriceType"));
        assertFalse(fields.containsKey("defaultPriceCurrency"));
    }

    @Test
    void defaultPriceArrivingLateClaimsTheEmptySlot() {
        RecordingIndex index = new RecordingIndex();
        ProductIndexService service =
            new ProductIndexService(index, new FakeProcessedEvents(), new FakeCategoryNames());
        service.upserted("product.product.created.v1:0:0",
            new ProductIndexService.ProductSnapshot("P1", null, null, "Widget", List.of(),
                List.of()));
        // non-default rows never claim the slot
        service.priceChanged("product.price.changed.v1:0:0", "P1",
            "LIST_PRICE", "EUR", "7.00", false);
        assertFalse(index.fields("P1").containsKey("defaultPrice"));

        service.priceChanged("product.price.changed.v1:0:1", "P1",
            "DEFAULT_PRICE", "USD", "8.00", false);
        assertEquals("8.00", index.fields("P1").get("defaultPrice"));
    }

    @Test
    void priceChangeForUnknownProductWaitsForItsCreatedEvent() {
        RecordingIndex index = new RecordingIndex();
        ProductIndexService service =
            new ProductIndexService(index, new FakeProcessedEvents(), new FakeCategoryNames());

        assertTrue(service.priceChanged("product.price.changed.v1:0:0", "MISSING",
            "DEFAULT_PRICE", "USD", "1.00", false));

        assertTrue(index.indexed.isEmpty());
        assertTrue(index.getCalls.containsKey("MISSING"));
    }

    @Test
    void priceChangeReplayIsSkipped() {
        RecordingIndex index = new RecordingIndex();
        FakeProcessedEvents processed = new FakeProcessedEvents();
        ProductIndexService service = new ProductIndexService(index, processed,
            new FakeCategoryNames());
        service.upserted("product.product.created.v1:0:0", snapshot("P1"));
        int indexedBefore = index.indexed.size();

        assertFalse(service.priceChanged("product.product.created.v1:0:0", "P1",
            "DEFAULT_PRICE", "USD", "1.00", false));
        assertEquals(indexedBefore, index.indexed.size());
    }

    @Test
    void categoryUpdateFeedsTheProjection() {
        FakeCategoryNames categories = new FakeCategoryNames();
        ProductIndexService service =
            new ProductIndexService(new RecordingIndex(), new FakeProcessedEvents(), categories);

        assertTrue(service.categoryUpdated("product.category.updated.v1:0:0", "CAT-LAP", "Laptops"));
        assertFalse(service.categoryUpdated("product.category.updated.v1:0:0", "CAT-LAP", "X"));
        assertEquals(Map.of("CAT-LAP", "Laptops"), categories.names);
    }
}
