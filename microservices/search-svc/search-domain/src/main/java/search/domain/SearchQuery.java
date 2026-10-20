package search.domain;

/** Validated query: non-blank text, sane limit, default index. */
public record SearchQuery(String query, String index, int limit) {

    public static final int MAX_LIMIT = 100;
    public static final int DEFAULT_LIMIT = 10;

    public SearchQuery {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("query must not be blank");
        }
        if (index == null || index.isBlank()) {
            index = SearchDocument.DEFAULT_INDEX;
        }
        if (limit <= 0) {
            limit = DEFAULT_LIMIT;
        }
        if (limit > MAX_LIMIT) {
            limit = MAX_LIMIT;
        }
    }
}
