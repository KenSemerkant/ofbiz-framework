package search.adapters.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import search.domain.SearchDocument;
import search.domain.SearchIndex;
import search.domain.SearchQuery;
import search.v1.SearchQueryGrpc;
import search.v1.SearchOuterClass.SearchRequest;
import search.v1.SearchOuterClass.SearchResponse;

/**
 * P1-F5-S1 acceptance, end to end against real OpenSearch + Kafka:
 * a document published to search.doc.indexed.v1 is indexed, REST and gRPC
 * queries return the hit, and reindex-from-topic rebuilds a wiped index.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SearchApiTest {

    static final String TOPIC = "search.doc.indexed.v1";

    @Container
    static final KafkaContainer KAFKA = new KafkaContainer();

    // consumer state since P3-F3-S1: processed_events + category projection
    @Container
    static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("search").withUsername("search").withPassword("search");

    // Testcontainers 2.x has no opensearch module — plain container, HTTP on 9200
    @Container
    static final GenericContainer<?> OPENSEARCH =
        new GenericContainer<>("opensearchproject/opensearch:2.11.1")
            .withExposedPorts(9200)
            .withEnv("DISABLE_SECURITY_PLUGIN", "true")
            .withEnv("discovery.type", "single-node")
            .withEnv("OPENSEARCH_JAVA_OPTS", "-Xms512m -Xmx512m")
            .withStartupTimeout(Duration.ofMinutes(3))
            .waitingFor(org.testcontainers.containers.wait.strategy.Wait
                .forHttp("/").forPort(9200).forStatusCode(200));

    @Autowired
    KafkaTemplate<String, String> kafka;
    @Autowired
    SearchIndex searchIndex;

    @Value("${local.server.port}")
    int port;
    @Value("${search.grpc.port}")
    int grpcPort;

    private final HttpClient http = HttpClient.newBuilder().build();

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) throws Exception {
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("search.opensearch.uris",
            () -> "http://" + OPENSEARCH.getHost() + ":" + OPENSEARCH.getMappedPort(9200));
        try (var socket = new java.net.ServerSocket(0)) {
            registry.add("search.grpc.port", () -> String.valueOf(socket.getLocalPort()));
        }
    }

    @AfterAll
    static void afterAll() {
        // index lives in the container; nothing to clean up. Kept as a hook
        // for future shared-state cleanup.
    }

    @Test
    void eventIsIndexedAndBothApisReturnTheHit() throws Exception {
        String docId = "doc-" + UUID.randomUUID();
        publish(docId);

        assertTrue(awaitFound(docId, 30), "document " + docId + " never became searchable");

        HttpResponse<String> rest = http.send(
            HttpRequest.newBuilder(URI.create("http://localhost:" + port
                    + "/api/v1/search?q=thinkpad&limit=5")).GET().build(),
            HttpResponse.BodyHandlers.ofString());
        assertEquals(200, rest.statusCode(), rest.body());
        assertTrue(rest.body().contains(docId), "REST response must contain the doc: " + rest.body());
        assertTrue(rest.body().contains("\"score\""), "hits must carry a score: " + rest.body());

        ManagedChannel channel = ManagedChannelBuilder
            .forAddress("localhost", grpcPort).usePlaintext().build();
        try {
            SearchResponse grpc = SearchQueryGrpc.newBlockingStub(channel)
                .withDeadlineAfter(10, java.util.concurrent.TimeUnit.SECONDS)
                .search(SearchRequest.newBuilder().setQuery("thinkpad").setLimit(5).build());
            assertTrue(grpc.getHitsList().stream()
                    .anyMatch(hit -> hit.getDocId().equals(docId)),
                "gRPC response must contain the doc: " + grpc.getHitsList());
        } finally {
            channel.shutdownNow();
        }
    }

    @Test
    void reindexRebuildsWipedIndex() throws Exception {
        String docId = "doc-" + UUID.randomUUID();
        publish(docId);
        assertTrue(awaitFound(docId, 30), "document " + docId + " never became searchable");

        // wipe the index behind the service's back, then rebuild from the topic
        searchIndex.recreate(SearchDocument.DEFAULT_INDEX);
        assertTrue(awaitGone(docId, 10), "index wipe left doc " + docId + " behind");

        HttpResponse<String> reindex = http.send(
            HttpRequest.newBuilder(URI.create("http://localhost:" + port
                    + "/api/v1/search/reindex")).POST(HttpRequest.BodyPublishers.noBody()).build(),
            HttpResponse.BodyHandlers.ofString());
        assertEquals(200, reindex.statusCode(), reindex.body());

        assertTrue(awaitFound(docId, 30),
            "reindex-from-topic did not rebuild doc " + docId + ": " + reindex.body());
    }

    private void publish(String docId) {
        kafka.send(TOPIC, docId,
            "{\"docId\":\"" + docId + "\",\"fields\":{\"name\":\"thinkpad laptop\"}}");
    }

    private boolean found(String docId) {
        return searchIndex.search(new SearchQuery("thinkpad", null, 100)).stream()
            .anyMatch(result -> result.docId().equals(docId));
    }

    private boolean awaitFound(String docId, int timeoutSeconds) throws InterruptedException {
        return await(docId, true, timeoutSeconds);
    }

    private boolean awaitGone(String docId, int timeoutSeconds) throws InterruptedException {
        return await(docId, false, timeoutSeconds);
    }

    private boolean await(String docId, boolean wantPresent, int timeoutSeconds)
            throws InterruptedException {
        for (int i = 0; i < timeoutSeconds * 2; i++) {
            if (found(docId) == wantPresent) {
                return true;
            }
            Thread.sleep(500);
        }
        return false;
    }
}
