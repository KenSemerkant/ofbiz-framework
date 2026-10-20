package search.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SearchDomainTest {

    @Test
    void queryRejectsBlankText() {
        assertThrows(IllegalArgumentException.class, () -> new SearchQuery("  ", "idx", 10));
    }

    @Test
    void queryDefaultsIndexAndLimit() {
        SearchQuery query  = new SearchQuery("laptop", null, 0);
        assertEquals(SearchDocument.DEFAULT_INDEX, query.index());
        assertEquals(SearchQuery.DEFAULT_LIMIT, query.limit());
    }

    @Test
    void queryClampsLimitToMaximum() {
        assertEquals(SearchQuery.MAX_LIMIT, new SearchQuery("laptop", "idx", 10_000).limit());
    }

    @Test
    void documentValidatesAndNormalises() {
        Instant updated = Instant.parse("2026-10-20T00:00:00Z");
        SearchDocument document = new SearchDocument("doc-1", " ", Map.of("name", "laptop"), updated);
        assertEquals(SearchDocument.DEFAULT_INDEX, document.index());
        assertEquals(updated, document.updatedAt());

        assertThrows(IllegalArgumentException.class,
            () -> new SearchDocument(" ", "idx", Map.of("name", "x"), updated));
        assertThrows(IllegalArgumentException.class,
            () -> new SearchDocument("doc-1", "idx", Map.of(), updated));
    }

    @Test
    void documentDefaultsTimestampAndCopiesFields() {
        Map<String, String> fields = new HashMap<>();
        fields.put("name", "laptop");

        SearchDocument document = new SearchDocument("doc-1", "idx", fields, null);
        fields.put("name", "changed");

        assertNotNull(document.updatedAt());
        assertEquals("laptop", document.fields().get("name"), "fields must be defensively copied");
        assertThrows(UnsupportedOperationException.class,
            () -> document.fields().put("other", "value"));
    }

    @Test
    void resultDefaultsSourceAndValidatesId() {
        assertEquals("{}", new SearchResult("doc-1", 1.0, null).source());
        assertThrows(IllegalArgumentException.class, () -> new SearchResult(" ", 1.0, "{}"));
    }
}
