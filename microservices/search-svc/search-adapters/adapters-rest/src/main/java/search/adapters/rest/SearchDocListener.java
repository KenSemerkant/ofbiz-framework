package search.adapters.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import search.application.SearchService;
import search.domain.SearchDocument;

/** P1-F5-S1 AC a: index documents from the search.doc.indexed.v1 topic. */
@Component
public class SearchDocListener {

    private final SearchService searchService;
    private final ObjectMapper mapper = new ObjectMapper();

    public SearchDocListener(SearchService searchService) {
        this.searchService = searchService;
    }

    @KafkaListener(id = "search-doc-index", groupId = "search-svc", topics = "${search.topic}")
    public void on(String payload) {
        IndexEvent event = IndexEvent.parse(payload, mapper);
        searchService.index(
            new SearchDocument(event.docId(), event.index(), event.fields(), null));
    }
}
