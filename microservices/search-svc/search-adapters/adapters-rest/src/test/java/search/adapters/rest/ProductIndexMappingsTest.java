package search.adapters.rest;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.Test;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.ErrorResponse;
import org.opensearch.client.opensearch._types.OpenSearchException;
import org.opensearch.client.opensearch.indices.CreateIndexRequest;
import org.opensearch.client.opensearch.indices.CreateIndexResponse;
import org.opensearch.client.opensearch.indices.ExistsRequest;
import org.opensearch.client.transport.Endpoint;
import org.opensearch.client.transport.TransportOptions;
import org.opensearch.client.transport.OpenSearchTransport;
import search.application.ProductIndexService;

/**
 * P3-F3-S1 AC b: index creation is explicit and idempotent — missing index
 * gets the versioned mapping, existing one is left alone, a lost create
 * race is tolerated, real failures surface.
 */
class ProductIndexMappingsTest {

    /**
     * Transport fake dispatching on the request type — the real client code
     * runs against it. A null handler means "this endpoint must not be
     * called"; handlers return a response or throw.
     */
    private static OpenSearchTransport scripted(
            java.util.function.Function<Object, UnaryOperator<Object>> handlers) {
        return new OpenSearchTransport() {
            @Override
            @SuppressWarnings("unchecked")
            public <RequestT, ResponseT, ErrorT> ResponseT performRequest(
                    RequestT request, Endpoint<RequestT, ResponseT, ErrorT> endpoint,
                    TransportOptions options) throws IOException {
                UnaryOperator<Object> handler = handlers.apply(request);
                if (handler == null) {
                    throw new IllegalStateException(
                        "unexpected request " + request.getClass().getSimpleName());
                }
                Object outcome = handler.apply(request);
                if (outcome instanceof IOException io) {
                    throw io;
                }
                if (outcome instanceof RuntimeException runtime) {
                    throw runtime;
                }
                return (ResponseT) outcome;
            }

            @Override
            @SuppressWarnings("unchecked")
            public <RequestT, ResponseT, ErrorT> CompletableFuture<ResponseT> performRequestAsync(
                    RequestT request, Endpoint<RequestT, ResponseT, ErrorT> endpoint,
                    TransportOptions options) {
                Object outcome = handlers.apply(request).apply(request);
                return outcome instanceof IOException io
                    ? CompletableFuture.failedFuture(io)
                    : CompletableFuture.completedFuture((ResponseT) outcome);
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

    private static OpenSearchException error(int status) {
        return new OpenSearchException(new ErrorResponse.Builder()
            .status(status)
            .error(new org.opensearch.client.opensearch._types.ErrorCause.Builder()
                .type("resource_already_exists_exception").build())
            .build());
    }

    @Test
    void existingIndexIsLeftAlone() {
        var transport = scripted(request -> {
            if (request instanceof ExistsRequest) {
                return ignored -> new org.opensearch.client.transport.endpoints.BooleanResponse(
                    true);
            }
            return null; // indices.create must not be called
        });

        ProductIndexMappings.ensureProductsIndex(new OpenSearchClient(transport));
        // exists returned true — no create request, no exception
    }

    @Test
    void missingIndexIsCreatedWithTheMapping() {
        java.util.List<String> createdIndexes = new java.util.ArrayList<>();
        var transport = scripted(request -> {
            if (request instanceof ExistsRequest) {
                return ignored -> new org.opensearch.client.transport.endpoints.BooleanResponse(
                    false);
            }
            if (request instanceof CreateIndexRequest create) {
                createdIndexes.add(create.index());
                return ignored -> CreateIndexResponse.of(
                    r -> r.index(create.index()).acknowledged(true).shardsAcknowledged(true));
            }
            return null;
        });

        ProductIndexMappings.ensureProductsIndex(new OpenSearchClient(transport));

        org.junit.jupiter.api.Assertions.assertEquals(
            java.util.List.of(ProductIndexService.INDEX), createdIndexes);
    }

    @Test
    void lostCreateRaceIsTolerated() {
        var transport = scripted(request -> {
            if (request instanceof ExistsRequest) {
                return ignored -> new org.opensearch.client.transport.endpoints.BooleanResponse(
                    false);
            }
            if (request instanceof CreateIndexRequest) {
                return ignored -> error(400);
            }
            return null;
        });

        assertDoesNotThrow(
            () -> ProductIndexMappings.ensureProductsIndex(new OpenSearchClient(transport)));
    }

    @Test
    void realFailuresFailLoud() {
        var existsIo = scripted(request -> request instanceof ExistsRequest
            ? ignored -> new IOException("opensearch down") : null);
        assertThrows(IllegalStateException.class,
            () -> ProductIndexMappings.ensureProductsIndex(new OpenSearchClient(existsIo)));

        var createIo = scripted(request -> {
            if (request instanceof ExistsRequest) {
                return ignored -> new org.opensearch.client.transport.endpoints.BooleanResponse(
                    false);
            }
            return request instanceof CreateIndexRequest
                ? ignored -> new IOException("create failed") : null;
        });
        assertThrows(IllegalStateException.class,
            () -> ProductIndexMappings.ensureProductsIndex(new OpenSearchClient(createIo)));

        var serverError = scripted(request -> {
            if (request instanceof ExistsRequest) {
                return ignored -> new org.opensearch.client.transport.endpoints.BooleanResponse(
                    false);
            }
            return request instanceof CreateIndexRequest ? ignored -> error(500) : null;
        });
        assertThrows(OpenSearchException.class,
            () -> ProductIndexMappings.ensureProductsIndex(new OpenSearchClient(serverError)));
    }
}
