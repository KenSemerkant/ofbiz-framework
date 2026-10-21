package search.adapters.rest;

import org.opensearch.client.opensearch.OpenSearchClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import search.application.SearchService;
import search.domain.SearchIndex;

/** Wires the OpenSearch adapter behind the application use case. */
@Configuration
public class SearchConfig {

    @Bean
    SearchIndex searchIndex(OpenSearchClient client) {
        return new OpenSearchIndex(client);
    }

    @Bean
    SearchService searchService(SearchIndex searchIndex) {
        return new SearchService(searchIndex);
    }
}
