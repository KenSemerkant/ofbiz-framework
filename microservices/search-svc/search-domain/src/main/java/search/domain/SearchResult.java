package search.domain;

/** One search hit: the document id, its relevance score, and the stored source JSON. */
public record SearchResult(String docId, double score, String source) {

    public SearchResult {
        if (docId == null || docId.isBlank()) {
            throw new IllegalArgumentException("docId must not be blank");
        }
        if (source == null) {
            source = "{}";
        }
    }
}
