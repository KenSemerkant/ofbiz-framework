package content.adapters.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import content.adapters.rest.persistence.OutboxRepository;
import content.application.ObjectStore;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * P6-T4 CG slice: upload→publish→URL fetch roundtrip. Tree CRUD, binary
 * upload landing under contents/{id}, forward-only publish (re-publish and
 * publish-without-upload are typed 409s), DRAFT data fetch is a 409, folder
 * upload is a 409, and every state change leaves its outbox row. Relay
 * disabled — the outbox rows ARE the event evidence (order-svc test
 * convention); on-cluster is the P6 gate drill. The S3 port is an
 * in-memory fake — Garage wiring is platform/storage's verify script.
 */
@SpringBootTest(properties = "content.relay.enabled=false",
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class ContentApiTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    @LocalServerPort
    int port;

    @Autowired
    OutboxRepository outbox;

    final HttpClient http = HttpClient.newHttpClient();
    final ObjectMapper json = new ObjectMapper();

    /** Stands in for Garage so the slice needs no S3 server. */
    @TestConfiguration
    static class MemoryObjectStoreConfiguration {

        @Bean
        @Primary
        ObjectStore memoryObjectStore() {
            return new MemoryObjectStore();
        }
    }

    static final class MemoryObjectStore implements ObjectStore {
        final Map<String, byte[]> stored = new HashMap<>();

        @Override
        public void put(String key, byte[] bytes, String contentType) {
            stored.put(key, bytes.clone());
        }

        @Override
        public byte[] get(String key) {
            return stored.get(key);
        }

        @Override
        public boolean exists(String key) {
            return stored.containsKey(key);
        }
    }

    private JsonNode post(String path, String body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:%d%s".formatted(port, path)))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body)).build();
        return json.readTree(http.send(request, HttpResponse.BodyHandlers.ofString()).body());
    }

    private int status(String method, String path, String body, String contentType)
            throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:%d%s".formatted(port, path)))
            .header("Content-Type", contentType)
            .method(method, HttpRequest.BodyPublishers.ofString(body)).build();
        return http.send(request, HttpResponse.BodyHandlers.ofString()).statusCode();
    }

    private JsonNode get(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:%d%s".formatted(port, path))).GET().build();
        return json.readTree(http.send(request, HttpResponse.BodyHandlers.ofString()).body());
    }

    @Test
    void uploadPublishFetchRoundtripWithGuards() throws Exception {
        post("/api/v1/contents", """
            {"contentId":"C-F","contentTypeId":"FOLDER","contentName":"docs"}
            """);
        post("/api/v1/contents", """
            {"contentId":"C-1","contentTypeId":"DOCUMENT","contentName":"spec.pdf",
             "parentContentId":"C-F"}
            """);

        JsonNode tree = get("/api/v1/contents?parent=C-F");
        assertTrue(tree.isArray());
        assertEquals(1, tree.size());
        assertEquals("C-1", tree.get(0).path("contentId").asText());

        // DRAFT fetch is refused before the upload even exists
        assertEquals(409, status("GET", "/api/v1/contents/C-1/data", "", "*/*"));

        int uploaded = status("POST", "/api/v1/contents/C-1/upload",
            "hello content", "text/plain");
        assertEquals(200, uploaded);
        JsonNode afterUpload = get("/api/v1/contents/C-1");
        assertEquals("contents/C-1", afterUpload.path("s3Key").asText());
        assertEquals(13, afterUpload.path("sizeBytes").asLong());
        assertEquals("text/plain", afterUpload.path("contentType").asText());
        assertEquals("DRAFT", afterUpload.path("statusId").asText());

        // still DRAFT → data fetch stays a 409 until publish
        assertEquals(409, status("GET", "/api/v1/contents/C-1/data", "", "*/*"));

        JsonNode published = post("/api/v1/contents/C-1/publish", "{}");
        assertEquals("PUBLISHED", published.path("statusId").asText());
        assertEquals(409, status("POST", "/api/v1/contents/C-1/publish", "{}",
            "application/json"), "publish is forward-only");
        assertEquals(409, status("POST", "/api/v1/contents/C-1/upload", "x", "text/plain"),
            "published content is frozen");

        HttpRequest fetch = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:%d/api/v1/contents/C-1/data".formatted(port)))
            .GET().build();
        HttpResponse<String> data = http.send(fetch, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, data.statusCode());
        assertEquals("hello content", data.body());
        assertTrue(data.headers().firstValue("Content-Type").orElse("").startsWith(
            "text/plain"));

        assertTrue(outbox.findAll().stream().anyMatch(e -> "content.content.created.v1"
            .equals(e.getEventType())));
        assertTrue(outbox.findAll().stream().anyMatch(e -> "content.content.updated.v1"
            .equals(e.getEventType())));
        assertTrue(outbox.findAll().stream().anyMatch(e -> "content.content.published.v1"
            .equals(e.getEventType())));
    }

    @Test
    void folderUploadAndPublishWithoutUploadAreTyped409s() throws Exception {
        post("/api/v1/contents", """
            {"contentId":"C-F2","contentTypeId":"FOLDER","contentName":"docs2"}
            """);
        post("/api/v1/contents", """
            {"contentId":"C-2","contentTypeId":"DOCUMENT","contentName":"empty.pdf"}
            """);
        assertEquals(409, status("POST", "/api/v1/contents/C-F2/upload", "x", "text/plain"),
            "folders never hold bytes");
        assertEquals(409, status("POST", "/api/v1/contents/C-2/publish", "{}",
            "application/json"), "publish needs an upload");
    }

    @Test
    void crudGuardsAndTreeQueries() throws Exception {
        post("/api/v1/contents", """
            {"contentId":"C-3","contentTypeId":"DOCUMENT","contentName":"draft.pdf"}
            """);
        assertEquals(200, status("PUT", "/api/v1/contents/C-3",
            "{\"contentName\":\"renamed.pdf\"}", "application/json"));
        assertEquals("renamed.pdf", get("/api/v1/contents/C-3").path("contentName").asText());
        assertEquals(204, status("DELETE", "/api/v1/contents/C-3", "", "application/json"));
        assertEquals(404, status("GET", "/api/v1/contents/C-3", "", "application/json"));
        assertEquals(404, status("GET", "/api/v1/contents/C-404", "", "application/json"));
        assertEquals(404, status("POST", "/api/v1/contents",
            "{\"contentId\":\"C-X\",\"contentTypeId\":\"DOCUMENT\",\"contentName\":\"x\","
                + "\"parentContentId\":\"C-NONE\"}", "application/json"),
            "orphan parent is a 404");
        assertEquals(404, status("POST", "/api/v1/contents/C-404/upload", "x", "text/plain"));
        assertTrue(get("/api/v1/contents").isArray());
    }
}
