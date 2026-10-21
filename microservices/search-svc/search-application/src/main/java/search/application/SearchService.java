package search.application;

import java.util.List;
import search.domain.SearchDocument;
import search.domain.SearchIndex;
import search.domain.SearchQuery;
import search.domain.SearchResult;

/**
 * P1-F5-S1 use case: consumer and query adapters go through this seam, the
 * domain port does the work. Pass-through today; dedupe/enrichment would be
 * added here, not in an adapter.
 */
public class SearchService {

    private final SearchIndex index;

    public SearchService(SearchIndex index) {
        this.index = index;
    }

    public void index(SearchDocument document) {
        index.index(document);
    }

    public List<SearchResult> search(SearchQuery query) {
        return index.search(query);
    }

    public void recreate(String indexName) {
        index.recreate(indexName);
    }
}
