package search.adapters.rest;

import org.opensearch.client.opensearch.OpenSearchClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import search.application.CategoryNames;
import search.application.ProcessedEvents;
import search.application.ProductIndexService;
import search.domain.SearchIndex;

/** Wires the product indexer behind the application use case. */
@Configuration
public class ProductIndexConfig {

    @Bean
    ProductIndexService productIndexService(SearchIndex searchIndex, ProcessedEvents processed,
            CategoryNames categories, OpenSearchClient client) {
        // before the listener containers start, so dynamic mapping can never
        // win the race against the versioned explicit one
        ProductIndexMappings.ensureProductsIndex(client);
        return new ProductIndexService(searchIndex, processed, categories);
    }
}
