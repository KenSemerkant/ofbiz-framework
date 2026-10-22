package search.adapters.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.ErrorResponse;
import org.opensearch.client.opensearch._types.OpenSearchException;
import org.opensearch.client.transport.Endpoint;
import org.opensearch.client.transport.TransportOptions;
import org.opensearch.client.transport.OpenSearchTransport;
import search.domain.SearchDocument;
import search.domain.SearchQuery;

/**
 * Unit coverage for adapter paths the OpenSearch-container test doesn't
 * reach: the search 404 mapping, IOException wrapping, recreate's tolerant
 * delete, IndexEvent.parse rejection, and the DLQ error-handler bean.
 * The fakes sit at the transport interface — the real client code runs.
 */
class SearchAdaptersUnitTest {

    private static OpenSearchTransport throwing(Exception failure) {
        return new OpenSearchTransport() {
            @Override
            @SuppressWarnings("unchecked")
            public <RequestT, ResponseT, ErrorT> ResponseT performRequest(
                    RequestT request, Endpoint<RequestT, ResponseT, ErrorT> endpoint,
                    TransportOptions options) throws IOException {
                if (failure instanceof IOException io) {
                    throw io;
                }
                throw (OpenSearchException) failure;
            }

            @Override
            public <RequestT, ResponseT, ErrorT> CompletableFuture<ResponseT> performRequestAsync(
                    RequestT request, Endpoint<RequestT, ResponseT, ErrorT> endpoint,
                    TransportOptions options) {
                return CompletableFuture.failedFuture(failure);
            }

            @Override
            public org.opensearch.client.json.JsonpMapper jsonpMapper() {
                return null;
            }

            @Override
            public TransportOptions options() {
                return null;
            }

            @Override
            public void close() {
            }
        };
    }

    private static SearchDocument doc() {
        return new SearchDocument("d1", "idx", Map.of("name", "x"),
            java.time.Instant.parse("2026-10-21T10:00:00Z"));
    }

    @Test
    void ioFailuresWrapAsIllegalState() {
        var index = new OpenSearchIndex(new OpenSearchClient(throwing(new IOException("boom"))));
        assertThrows(IllegalStateException.class, () -> index.index(doc()));
        assertThrows(IllegalStateException.class,
            () -> index.search(new SearchQuery("q", "idx", 5)));
        // recreate: tolerant delete, then create surfaces the failure
        assertThrows(IllegalStateException.class, () -> index.recreate("idx"));
    }

    @Test
    void searchOnMissingIndexReturnsEmpty() {
        var error = new OpenSearchException(new ErrorResponse.Builder()
            .status(404)
            .error(new org.opensearch.client.opensearch._types.ErrorCause.Builder().type("index_not_found_exception").build())
            .build());
        var index = new OpenSearchIndex(new OpenSearchClient(throwing(error)));
        assertTrue(index.search(new SearchQuery("q", "missing-idx", 5)).isEmpty());
    }

    @Test
    void indexEventParseRejectsMalformedPayload() {
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var event = IndexEvent.parse(
            "{\"docId\":\"d\",\"index\":\"i\",\"fields\":{\"a\":\"b\"}}", mapper);
        assertEquals("d", event.docId());
        assertThrows(IllegalArgumentException.class,
            () -> IndexEvent.parse("not-json", mapper));
    }

    @Test
    void dlqErrorHandlerBeanBuilds() {
        assertNotNull(new KafkaConfig().searchErrorHandler(
            new org.springframework.kafka.core.ProducerFactory<Object, Object>() {
                @Override
                public org.apache.kafka.clients.producer.Producer<Object, Object> createProducer() {
                    throw new IllegalStateException("recoverer never produces");
                }

                @Override
                public org.apache.kafka.clients.producer.Producer<Object, Object> createProducer(
                        String txIdPrefix) {
                    throw new IllegalStateException("recoverer never produces");
                }
            }));
    }

    @Test
    void deleteOfMissingDocIsTolerated() {
        var error = new OpenSearchException(new ErrorResponse.Builder()
            .status(404)
            .error(new org.opensearch.client.opensearch._types.ErrorCause.Builder()
                .type("resource_not_found_exception").build())
            .build());
        var index = new OpenSearchIndex(new OpenSearchClient(throwing(error)));
        index.delete("products", "P1"); // must not throw — already gone is success
    }

    @Test
    void deleteAndGetIoFailuresWrapAsIllegalState() {
        var index = new OpenSearchIndex(new OpenSearchClient(throwing(new IOException("boom"))));
        assertThrows(IllegalStateException.class, () -> index.delete("products", "P1"));
        assertThrows(IllegalStateException.class, () -> index.get("products", "P1"));
    }

    @Test
    void getOnMissingDocReturnsEmpty() {
        var error = new OpenSearchException(new ErrorResponse.Builder()
            .status(404)
            .error(new org.opensearch.client.opensearch._types.ErrorCause.Builder()
                .type("resource_not_found_exception").build())
            .build());
        var index = new OpenSearchIndex(new OpenSearchClient(throwing(error)));
        assertTrue(index.get("products", "missing").isEmpty());
    }

    @Test
    void getFlattensSourceToStringFields() {
        com.fasterxml.jackson.databind.node.ObjectNode source =
            com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
        source.put("productId", "P1");
        source.put("defaultPrice", "1099.99");
        var getResponse = org.opensearch.client.opensearch.core.GetResponse
            .<com.fasterxml.jackson.databind.node.ObjectNode>of(g -> g
                .index("products").id("P1").found(true).source(source));
        var transport = new OpenSearchTransport() {
            @Override
            @SuppressWarnings("unchecked")
            public <RequestT, ResponseT, ErrorT> ResponseT performRequest(
                    RequestT request, Endpoint<RequestT, ResponseT, ErrorT> endpoint,
                    TransportOptions options) {
                return (ResponseT) getResponse;
            }

            @Override
            @SuppressWarnings("unchecked")
            public <RequestT, ResponseT, ErrorT> CompletableFuture<ResponseT> performRequestAsync(
                    RequestT request, Endpoint<RequestT, ResponseT, ErrorT> endpoint,
                    TransportOptions options) {
                return CompletableFuture.completedFuture((ResponseT) getResponse);
            }

            @Override
            public org.opensearch.client.json.JsonpMapper jsonpMapper() {
                return null;
            }

            @Override
            public TransportOptions options() {
                return null;
            }

            @Override
            public void close() {
            }
        };

        Map<String, String> fields = new OpenSearchIndex(new OpenSearchClient(transport))
            .get("products", "P1");

        assertEquals(Map.of("productId", "P1", "defaultPrice", "1099.99"), fields);
    }
}
