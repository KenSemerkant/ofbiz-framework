package product.adapters.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** AC (P3-F1-S3): store settings API read/write. */
@SpringBootTest(properties = "product.relay.enabled=false",
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class StoreApiTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    @LocalServerPort
    int port;

    private final HttpClient client = HttpClient.newHttpClient();

    private HttpResponse<String> postStore(String body) throws Exception {
        return client.send(HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + port + "/api/v1/stores"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> getStore(String storeId) throws Exception {
        return client.send(HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + port + "/api/v1/stores/" + storeId))
            .GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void storeSettingsReadWriteRoundtrip() throws Exception {
        assertEquals(201, postStore("""
            {"productStoreId":"SHOP","storeName":"Web Store",
             "defaultCurrencyUomId":"USD","inventoryFacilityId":"WH1"}""")
            .statusCode());

        HttpResponse<String> fetched = getStore("SHOP");
        assertEquals(200, fetched.statusCode());
        assertTrue(fetched.body().contains("Web Store"));
        assertTrue(fetched.body().contains("WH1"));

        // write again: settings update in place
        assertEquals(201, postStore("""
            {"productStoreId":"SHOP","storeName":"Retail Store",
             "defaultCurrencyUomId":"EUR"}""").statusCode());
        assertTrue(getStore("SHOP").body().contains("Retail Store"));
        assertTrue(getStore("SHOP").body().contains("EUR"));
    }

    @Test
    void unknownStoreIsNotFoundAndInvalidIsRejected() throws Exception {
        assertEquals(404, getStore("nope").statusCode());
        assertEquals(400, postStore(
            "{\"productStoreId\":\"SHOP\",\"storeName\":\" \"}").statusCode());
    }

    @Test
    void deleteStoreRemovesRow() throws Exception {
        assertEquals(201, postStore("""
            {"productStoreId":"DEMO","storeName":"Demo Store"}""").statusCode());
        assertEquals(204, client.send(HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + port + "/api/v1/stores/DEMO"))
            .DELETE().build(), HttpResponse.BodyHandlers.ofString()).statusCode());
        assertEquals(404, getStore("DEMO").statusCode());
        assertEquals(404, client.send(HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + port + "/api/v1/stores/nope"))
            .DELETE().build(), HttpResponse.BodyHandlers.ofString()).statusCode());
    }
}
