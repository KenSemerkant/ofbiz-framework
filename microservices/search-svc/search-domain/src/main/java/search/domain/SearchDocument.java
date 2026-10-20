package search.domain;

import java.time.Instant;
import java.util.Map;

/** Document to be indexed. Pure domain value — no framework imports (ADR-007). */
public record SearchDocument(String docId, String index, Map<String, String> fields, Instant updatedAt) {

    /** Default physical index backing the search context. */
    public static final String DEFAULT_INDEX = "search-docs";

    public SearchDocument {
        if (docId == null || docId.isBlank()) {
            throw new IllegalArgumentException("docId must not be blank");
        }
        if (index == null || index.isBlank()) {
            index = DEFAULT_INDEX;
        }
        fields = fields == null ? Map.of() : Map.copyOf(fields);
        if (fields.isEmpty()) {
            throw new IllegalArgumentException("fields must not be empty");
        }
        if (updatedAt == null) {
            updatedAt = Instant.now();
        }
    }
}
