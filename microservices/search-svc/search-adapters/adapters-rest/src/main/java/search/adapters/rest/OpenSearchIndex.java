package search.adapters.rest;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.OpenSearchException;
import search.domain.SearchDocument;
import search.domain.SearchIndex;
import search.domain.SearchQuery;
import search.domain.SearchResult;

/** OpenSearch implementation of the domain port. */
public class OpenSearchIndex implements SearchIndex {

    private final OpenSearchClient client;

    public OpenSearchIndex(OpenSearchClient client) {
        this.client = client;
    }

    @Override
    public void index(SearchDocument document) {
        Map<String, Object> source = new HashMap<>(document.fields());
        source.put("updatedAt", document.updatedAt().toString());
        try {
            client.index(request -> request
                .index(document.index())
                .id(document.docId())
                .document(source));
        } catch (IOException e) {
            throw new IllegalStateException("index call failed for " + document.docId(), e);
        }
    }

    @Override
    public List<SearchResult> search(SearchQuery query) {
        try {
            return doSearch(query);
        } catch (OpenSearchException e) {
            if (e.response() != null && e.response().status() == 404) {
                // index not created yet — no documents, no hits
                return List.of();
            }
            throw e;
        } catch (IOException e) {
            throw new IllegalStateException("search call failed for " + query.query(), e);
        }
    }

    private List<SearchResult> doSearch(SearchQuery query) throws IOException {
        var response = client.search(request -> request
                .index(query.index())
                .size(query.limit())
                .query(q -> q.queryString(qs -> qs.query(query.query()))),
            ObjectNode.class);
        return response.hits().hits().stream()
            .map(hit -> new SearchResult(hit.id(),
                hit.score() == null ? 0.0 : hit.score(),
                hit.source() == null ? "{}" : hit.source().toString()))
            .toList();
    }

    @Override
    public void recreate(String index) {
        try {
            client.indices().delete(request -> request.index(index));
        } catch (OpenSearchException | IOException e) {
            // index did not exist yet — nothing to drop
        }
        try {
            client.indices().create(request -> request.index(index));
        } catch (IOException e) {
            throw new IllegalStateException("create index failed for " + index, e);
        }
    }
}
