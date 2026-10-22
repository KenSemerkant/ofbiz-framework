package search.domain;

import java.util.List;
import java.util.Map;

/** Port: the search engine behind the context. Implemented by the OpenSearch adapter. */
public interface SearchIndex {

    void index(SearchDocument document);

    List<SearchResult> search(SearchQuery query);

    /** Drop and re-create the physical index (reindex-from-topic entry point). */
    void recreate(String index);

    /** Remove a document; a missing document or index is not an error. */
    void delete(String index, String docId);

    /**
     * Current source fields of a document, flattened to strings; empty map
     * when the document (or index) does not exist.
     */
    Map<String, String> get(String index, String docId);
}
