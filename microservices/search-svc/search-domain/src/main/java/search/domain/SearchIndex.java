package search.domain;

import java.util.List;

/** Port: the search engine behind the context. Implemented by the OpenSearch adapter. */
public interface SearchIndex {

    void index(SearchDocument document);

    List<SearchResult> search(SearchQuery query);

    /** Drop and re-create the physical index (reindex-from-topic entry point). */
    void recreate(String index);
}
