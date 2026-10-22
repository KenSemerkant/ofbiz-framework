package search.application;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import search.domain.SearchDocument;
import search.domain.SearchIndex;

/**
 * P3-F3-S1 use case: turns product-svc events into documents of the
 * `products` index (field mapping versioned via the schemaVersion field —
 * see ProductIndexMappings in the adapter). Idempotent per event id
 * (topic:partition:offset). The OpenSearch write happens before the marker:
 * every indexed operation is a full-document last-write-wins, so a crash
 * between the two replays the same document, never a duplicate.
 *
 * Contract with product-svc payloads (JacksonProductSerializer):
 * created/updated carry the full product and are reindexed wholesale —
 * only what events carry is indexed, there are no fetch-backs. Description
 * and association fields are not part of the events, so they are not part
 * of the index. price.changed is a best-effort refresh of the default-price
 * fields (every price mutation also emits product.updated, so the next full
 * event is authoritative). category.updated feeds the name projection used
 * at index time — a renamed category reaches a product document when that
 * product is next written.
 */
public class ProductIndexService {

    /** Physical index backing catalog product search. */
    public static final String INDEX = "products";

    /** Bumped when the index mapping gains/changes fields (P3-F3-S1 AC). */
    public static final String SCHEMA_VERSION = "1";

    /** OFBiz convention: the price type catalog search shows by default. */
    static final String DEFAULT_PRICE_TYPE = "DEFAULT_PRICE";

    static final String FIELD_SCHEMA_VERSION = "schemaVersion";
    static final String FIELD_DEFAULT_PRICE = "defaultPrice";
    static final String FIELD_DEFAULT_PRICE_TYPE = "defaultPriceType";
    static final String FIELD_DEFAULT_PRICE_CURRENCY = "defaultPriceCurrency";

    private final SearchIndex index;
    private final ProcessedEvents processed;
    private final CategoryNames categories;

    public ProductIndexService(SearchIndex index, ProcessedEvents processed,
            CategoryNames categories) {
        this.index = index;
        this.processed = processed;
        this.categories = categories;
    }

    /** Typed snapshot of product.product.created/updated.v1 payloads. */
    public record ProductSnapshot(String productId, String productTypeId, String internalName,
            String productName, List<String> categoryIds, List<Price> prices) {
    }

    public record Price(String productPriceTypeId, String currencyUomId, String price) {
    }

    /** @return true when the event was applied now (false = replay skip). */
    public boolean upserted(String eventId, ProductSnapshot snapshot) {
        if (processed.seen(eventId)) {
            return false;
        }
        index.index(new SearchDocument(snapshot.productId(), INDEX, docFields(snapshot), null));
        processed.record(eventId);
        return true;
    }

    /** @return true when the event was applied now (false = replay skip). */
    public boolean deleted(String eventId, String productId) {
        if (processed.seen(eventId)) {
            return false;
        }
        index.delete(INDEX, productId);
        processed.record(eventId);
        return true;
    }

    /**
     * Best-effort refresh of the default-price fields. Applies when the
     * changed row is the row the document shows (or claims the empty
     * DEFAULT_PRICE slot); other price types are not indexed, and a missing
     * document waits for its created/updated event.
     *
     * @return true when the event was applied now (false = replay skip).
     */
    public boolean priceChanged(String eventId, String productId, String productPriceTypeId,
            String currencyUomId, String price, boolean removed) {
        if (processed.seen(eventId)) {
            return false;
        }
        Map<String, String> doc = index.get(INDEX, productId);
        if (!doc.isEmpty()) {
            boolean showsThisRow = productPriceTypeId.equals(doc.get(FIELD_DEFAULT_PRICE_TYPE));
            if (showsThisRow || (!removed && DEFAULT_PRICE_TYPE.equals(productPriceTypeId)
                    && !doc.containsKey(FIELD_DEFAULT_PRICE))) {
                Map<String, String> updated = new LinkedHashMap<>(doc);
                if (removed) {
                    updated.remove(FIELD_DEFAULT_PRICE);
                    updated.remove(FIELD_DEFAULT_PRICE_TYPE);
                    updated.remove(FIELD_DEFAULT_PRICE_CURRENCY);
                } else {
                    updated.put(FIELD_DEFAULT_PRICE, price);
                    updated.put(FIELD_DEFAULT_PRICE_TYPE, productPriceTypeId);
                    updated.put(FIELD_DEFAULT_PRICE_CURRENCY, currencyUomId);
                }
                index.index(new SearchDocument(productId, INDEX, updated, null));
            }
        }
        processed.record(eventId);
        return true;
    }

    /** @return true when the event was applied now (false = replay skip). */
    public boolean categoryUpdated(String eventId, String categoryId, String name) {
        if (processed.seen(eventId)) {
            return false;
        }
        categories.save(categoryId, name);
        processed.record(eventId);
        return true;
    }

    private Map<String, String> docFields(ProductSnapshot snapshot) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put(FIELD_SCHEMA_VERSION, SCHEMA_VERSION);
        fields.put("productId", snapshot.productId());
        putIfPresent(fields, "productTypeId", snapshot.productTypeId());
        putIfPresent(fields, "internalName", snapshot.internalName());
        putIfPresent(fields, "productName", snapshot.productName());
        if (!snapshot.categoryIds().isEmpty()) {
            fields.put("categoryIds", String.join(",", snapshot.categoryIds()));
            Map<String, String> names = categories.resolve(snapshot.categoryIds());
            List<String> known = new ArrayList<>();
            for (String id : snapshot.categoryIds()) {
                String name = names.get(id);
                if (name != null) {
                    known.add(name);
                }
            }
            if (!known.isEmpty()) {
                fields.put("categoryNames", String.join(" ", known));
            }
        }
        pickDefaultPrice(snapshot.prices()).ifPresent(price -> {
            fields.put(FIELD_DEFAULT_PRICE, price.price());
            fields.put(FIELD_DEFAULT_PRICE_TYPE, price.productPriceTypeId());
            fields.put(FIELD_DEFAULT_PRICE_CURRENCY, price.currencyUomId());
        });
        return fields;
    }

    private static void putIfPresent(Map<String, String> fields, String key, String value) {
        if (value != null) {
            fields.put(key, value);
        }
    }

    private static java.util.Optional<Price> pickDefaultPrice(List<Price> prices) {
        return prices.stream()
            .filter(price -> DEFAULT_PRICE_TYPE.equals(price.productPriceTypeId()))
            .findFirst()
            .or(() -> prices.stream().findFirst());
    }
}
