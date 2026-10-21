package search.adapters.rest;

import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import search.application.SearchService;
import search.domain.SearchQuery;
import search.domain.SearchResult;

/** North-south query API (FUTURESTATE-ARCHITECTURE §6.3: /api/v1/search). */
@RestController
public class SearchController {

    private final SearchService searchService;
    private final ReindexRunner reindexRunner;

    public SearchController(SearchService searchService, ReindexRunner reindexRunner) {
        this.searchService = searchService;
        this.reindexRunner = reindexRunner;
    }

    public record SearchResponse(String query, List<Hit> hits) {
        public record Hit(String docId, double score, String source) {}
    }

    @GetMapping("/api/v1/search")
    public SearchResponse search(@RequestParam String q,
            @RequestParam(required = false) String index,
            @RequestParam(required = false, defaultValue = "0") int limit) {
        List<SearchResult> results = searchService.search(new SearchQuery(q, index, limit));
        return new SearchResponse(q, results.stream()
            .map(result -> new SearchResponse.Hit(
                result.docId(), result.score(), result.source()))
            .toList());
    }

    @PostMapping("/api/v1/search/reindex")
    public Map<String, Integer> reindex() {
        return Map.of("indexed", reindexRunner.reindex());
    }
}
