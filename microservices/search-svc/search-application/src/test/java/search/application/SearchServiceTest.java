package search.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import search.domain.SearchDocument;
import search.domain.SearchIndex;
import search.domain.SearchQuery;
import search.domain.SearchResult;

/** The use case delegates to the SearchIndex port; behavior lives in the domain. */
class SearchServiceTest {

    static final class RecordingIndex implements SearchIndex {
        final List<SearchDocument> indexed = new ArrayList<>();
        final List<SearchQuery> searches = new ArrayList<>();
        String recreated;
        List<SearchResult> results = List.of(new SearchResult("doc-1", 1.0, "{}"));

        @Override
        public void index(SearchDocument document) {
            indexed.add(document);
        }

        @Override
        public List<SearchResult> search(SearchQuery query) {
            searches.add(query);
            return results;
        }

        @Override
        public void recreate(String index) {
            recreated = index;
        }

        @Override
        public void delete(String index, String docId) {
        }

        @Override
        public Map<String, String> get(String index, String docId) {
            return Map.of();
        }
    }

    @Test
    void indexDelegatesToPort() {
        RecordingIndex port = new RecordingIndex();
        SearchService service = new SearchService(port);
        SearchDocument document =
            new SearchDocument("doc-1", null, Map.of("name", "laptop"), Instant.now());

        service.index(document);

        assertEquals(List.of(document), port.indexed);
    }

    @Test
    void searchDelegatesToPort() {
        RecordingIndex port = new RecordingIndex();
        SearchService service = new SearchService(port);

        List<SearchResult> results = service.search(new SearchQuery("laptop", null, 0));

        assertSame(port.results, results);
        assertEquals("laptop", port.searches.get(0).query());
        assertTrue(port.searches.get(0).limit() > 0);
    }

    @Test
    void recreateDelegatesToPort() {
        RecordingIndex port = new RecordingIndex();
        SearchService service = new SearchService(port);

        service.recreate("search-docs");

        assertEquals("search-docs", port.recreated);
    }
}
