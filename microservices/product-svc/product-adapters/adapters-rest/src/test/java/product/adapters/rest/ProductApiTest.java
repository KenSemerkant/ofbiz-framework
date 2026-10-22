package product.adapters.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import product.adapters.rest.persistence.OutboxEntryEntity;
import product.adapters.rest.persistence.OutboxRepository;

/**
 * AC (P3-F1-S1): product upsert/fetch via REST and the outbox row lands in
 * the same transaction; category upsert emits product.category.updated; the
 * category tree query returns the nested subtree (relay disabled — the
 * outbox row IS the event evidence). Boot 4 test style follows the repo
 * convention: plain HttpClient, no MockMvc/TestRestTemplate.
 */
@SpringBootTest(properties = "product.relay.enabled=false",
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class ProductApiTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    @LocalServerPort
    int port;

    @Autowired
    OutboxRepository outbox;

    private final HttpClient client = HttpClient.newHttpClient();

    private HttpResponse<String> postProduct(String body) throws Exception {
        return client.send(HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + port + "/api/v1/products"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> getProduct(String productId) throws Exception {
        return client.send(HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + port + "/api/v1/products/" + productId))
            .GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> postCategory(String body) throws Exception {
        return client.send(HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + port + "/api/v1/categories"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> getTree(String categoryId) throws Exception {
        return client.send(HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + port + "/api/v1/categories/" + categoryId
                + "/tree"))
            .GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> getCategory(String categoryId) throws Exception {
        return client.send(HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + port + "/api/v1/categories/" + categoryId))
            .GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> delete(String path) throws Exception {
        return client.send(HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + port + path))
            .DELETE().build(), HttpResponse.BodyHandlers.ofString());
    }

    /** Outbox rows for one aggregate in insertion (id) order — findAll() is unordered. */
    private List<OutboxEntryEntity> outboxFor(String aggregateKey) {
        return outbox.findAll().stream()
            .filter(e -> aggregateKey.equals(e.getAggregateKey()))
            .sorted(Comparator.comparing(OutboxEntryEntity::getId))
            .toList();
    }

    @Test
    void upsertThenFetchRoundtripEmitsCreatedThenUpdated() throws Exception {
        HttpResponse<String> created = postProduct("""
            {"productId":"WIDGET","productTypeId":"FINISHED_GOOD",
             "internalName":"Widget 1.0","productName":"Widget",
             "categoryIds":["TOOLS"],
             "associations":[{"productIdTo":"GIZMO","productAssocTypeId":"PRODUCT_VARIANT",
              "fromDate":"2026-01-01T00:00:00Z"}]}""");
        assertEquals(201, created.statusCode());
        assertTrue(created.body().contains("Widget 1.0"));
        assertTrue(created.body().contains("GIZMO"));
        assertEquals("product.product.created.v1", outboxFor("WIDGET").get(0).getEventType());

        assertEquals(201, postProduct("""
            {"productId":"WIDGET","productTypeId":"FINISHED_GOOD",
             "internalName":"Widget 2.0"}""").statusCode());
        assertEquals("product.product.updated.v1", outboxFor("WIDGET").get(1).getEventType());

        HttpResponse<String> fetched = getProduct("WIDGET");
        assertEquals(200, fetched.statusCode());
        assertTrue(fetched.body().contains("Widget 2.0"));
        // the replace-upsert dropped the membership and the association
        assertTrue(fetched.body().contains("\"associations\":[]"));
    }

    @Test
    void unknownProductIsNotFound() throws Exception {
        assertEquals(404, getProduct("unknown").statusCode());
    }

    @Test
    void invalidProductIsRejected() throws Exception {
        assertEquals(400, postProduct(
            "{\"productId\":\" \",\"productTypeId\":\"X\",\"internalName\":\"n\"}")
            .statusCode());
    }

    @Test
    void categoryTreeQueryReturnsNestedTreeAndEmitsEvents() throws Exception {
        assertEquals(201, postCategory(
            "{\"productCategoryId\":\"ROOT\",\"categoryName\":\"Root\"}").statusCode());
        assertEquals(201, postCategory(
            "{\"productCategoryId\":\"TOOLS\",\"primaryParentCategoryId\":\"ROOT\","
                + "\"categoryName\":\"Tools\"}").statusCode());
        assertEquals(201, postCategory(
            "{\"productCategoryId\":\"DRILLS\",\"primaryParentCategoryId\":\"TOOLS\","
                + "\"categoryName\":\"Drills\"}").statusCode());

        assertEquals("product.category.updated.v1",
            outboxFor("TOOLS").get(0).getEventType());

        HttpResponse<String> tree = getTree("ROOT");
        assertEquals(200, tree.statusCode());
        assertTrue(tree.body().contains("\"categoryName\":\"Root\""));
        assertTrue(tree.body().contains("\"productCategoryId\":\"DRILLS\""));

        assertEquals(404, getTree("nope").statusCode());
    }

    @Test
    void deleteProductRemovesRowAndEmitsDeletedEvent() throws Exception {
        assertEquals(201, postProduct("""
            {"productId":"GONE","productTypeId":"FINISHED_GOOD","internalName":"Goner"}""")
            .statusCode());

        assertEquals(204, delete("/api/v1/products/GONE").statusCode());
        assertEquals(404, getProduct("GONE").statusCode());
        List<OutboxEntryEntity> gone = outboxFor("GONE");
        assertEquals("product.product.deleted.v1", gone.get(gone.size() - 1).getEventType());
        assertTrue(gone.get(gone.size() - 1).getPayload().contains("\"productId\":\"GONE\""));
    }

    @Test
    void deleteUnknownProductIsNotFound() throws Exception {
        assertEquals(404, delete("/api/v1/products/nope").statusCode());
    }

    @Test
    void deleteCategoryOnlyWhenLeafWithoutMembers() throws Exception {
        postCategory("{\"productCategoryId\":\"ROOT2\",\"categoryName\":\"Root\"}");
        postCategory("{\"productCategoryId\":\"TOOLS2\",\"primaryParentCategoryId\":\"ROOT2\","
            + "\"categoryName\":\"Tools\"}");
        postCategory("{\"productCategoryId\":\"DRILLS2\",\"primaryParentCategoryId\":\"TOOLS2\","
            + "\"categoryName\":\"Drills\"}");
        assertEquals(201, postProduct("""
            {"productId":"WIDGET2","productTypeId":"FINISHED_GOOD","internalName":"w",
             "categoryIds":["TOOLS2"]}""").statusCode());

        // not a leaf: has a child and a member product
        assertEquals(409, delete("/api/v1/categories/TOOLS2").statusCode());
        // still has children
        assertEquals(409, delete("/api/v1/categories/ROOT2").statusCode());
        assertEquals(200, getCategory("TOOLS2").statusCode());

        // leaf without members deletes; no event (category delete is not in
        // the topic set)
        assertEquals(204, delete("/api/v1/categories/DRILLS2").statusCode());
        assertEquals(404, getCategory("DRILLS2").statusCode());
        assertTrue(outboxFor("DRILLS2").stream()
            .allMatch(e -> "product.category.updated.v1".equals(e.getEventType())));

        assertEquals(404, delete("/api/v1/categories/nope").statusCode());
    }

    @Test
    void membershipProbeDoesNotTreatWildcardsInCategoryIdsAsPatterns() throws Exception {
        // a naive LIKE probe would see "WILD%" as a pattern matching "WILDX"
        assertEquals(201, postCategory(
            "{\"productCategoryId\":\"WILD%\",\"categoryName\":\"Wildcard\"}").statusCode());
        assertEquals(201, postCategory(
            "{\"productCategoryId\":\"WILDX\",\"categoryName\":\"Plain\"}").statusCode());
        assertEquals(201, postProduct("""
            {"productId":"WP","productTypeId":"FINISHED_GOOD","internalName":"w",
             "categoryIds":["WILDX"]}""").statusCode());

        // WILD% is a leaf with NO member products — % must not widen the probe
        assertEquals(204, delete("/api/v1/categories/WILD%25").statusCode());
        // the actual member still guards WILDX
        assertEquals(409, delete("/api/v1/categories/WILDX").statusCode());

        // and a literal wildcard id must still match its own membership
        assertEquals(201, postCategory(
            "{\"productCategoryId\":\"P%T\",\"categoryName\":\"Literal\"}").statusCode());
        assertEquals(201, postProduct("""
            {"productId":"LP","productTypeId":"FINISHED_GOOD","internalName":"l",
             "categoryIds":["P%T"]}""").statusCode());
        assertEquals(409, delete("/api/v1/categories/P%25T").statusCode());
    }

    @Test
    void outboxPayloadsAreValidJsonForHostileFields() throws Exception {
        // quotes and backslashes in every user-supplied field
        assertEquals(201, postProduct("""
            {"productId":"W\\"Q\\\\T","productTypeId":"FINISHED_GOOD","internalName":"n",
             "categoryIds":["C\\"A"],
             "prices":[{"productPriceTypeId":"TY\\"PE","currencyUomId":"US\\"D",
              "fromDate":"2026-01-01T00:00:00Z","price":1.00}]}""").statusCode());
        assertEquals(201, postCategory(
            "{\"productCategoryId\":\"CAT\\\"Q\",\"categoryName\":\"Sharp \\\"Tools\\\" \\\\ Best\"}")
            .statusCode());

        tools.jackson.databind.ObjectMapper mapper = new tools.jackson.databind.ObjectMapper();
        for (OutboxEntryEntity entry : outboxFor("W\"Q\\T")) {
            mapper.readTree(entry.getPayload()); // invalid JSON would throw
        }
        mapper.readTree(outboxFor("CAT\"Q").get(0).getPayload());

        assertEquals("W\"Q\\T", mapper.readTree(outboxFor("W\"Q\\T").get(0).getPayload())
            .path("productId").asText());
        assertEquals("TY\"PE", mapper.readTree(outboxFor("W\"Q\\T").get(1).getPayload())
            .path("productPriceTypeId").asText());
        assertEquals("Sharp \"Tools\" \\ Best",
            mapper.readTree(outboxFor("CAT\"Q").get(0).getPayload())
                .path("categoryName").asText());
    }
}
