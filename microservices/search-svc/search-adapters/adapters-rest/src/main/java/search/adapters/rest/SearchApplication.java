package search.adapters.rest;

import org.apache.http.HttpHost;
import org.opensearch.client.RestClient;
import org.opensearch.client.json.jackson.JacksonJsonpMapper;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.transport.rest_client.RestClientTransport;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class SearchApplication {

    @Bean(destroyMethod = "close")
    RestClient restClient(@org.springframework.beans.factory.annotation.Value(
            "${search.opensearch.uris}") String uris) {
        return RestClient.builder(HttpHost.create(uris)).build();
    }

    @Bean
    OpenSearchClient openSearchClient(RestClient restClient) {
        return new OpenSearchClient(new RestClientTransport(restClient, new JacksonJsonpMapper()));
    }

    public static void main(String[] args) {
        SpringApplication.run(SearchApplication.class, args);
    }
}
