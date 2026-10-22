package product.adapters.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import product.adapters.rest.persistence.OutboxRepository;

/**
 * AC (P3-F1-S2): price resolution respects date-range rules via the API,
 * `product.price.changed.v1` is emitted on update, and the resolved price is
 * captured as an immutable snapshot at order time — a later price update
 * cannot change what a past instant resolves to.
 */
@SpringBootTest(properties = "product.relay.enabled=false",
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class PricingApiTest {

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

    private HttpResponse<String> getPrice(String productId, String query) throws Exception {
        return client.send(HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + port + "/api/v1/products/" + productId
                + "/price" + query))
            .GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void resolutionRespectsDateRanges() throws Exception {
        assertEquals(201, postProduct("""
            {"productId":"DRILL","productTypeId":"FINISHED_GOOD","internalName":"Drill",
             "prices":[
              {"productPriceTypeId":"DEFAULT_PRICE","currencyUomId":"USD",
               "fromDate":"2026-01-01T00:00:00Z","thruDate":"2026-06-01T00:00:00Z",
               "price":10.00},
              {"productPriceTypeId":"DEFAULT_PRICE","currencyUomId":"USD",
               "fromDate":"2026-06-01T00:00:00Z","price":12.00}]}""").statusCode());

        assertEquals(200, getPrice("DRILL",
            "?currency=USD&at=2026-03-01T00:00:00Z").statusCode());
        assertTrue(getPrice("DRILL", "?currency=USD&at=2026-03-01T00:00:00Z")
            .body().contains("\"price\":10.0"));
        assertTrue(getPrice("DRILL", "?currency=USD&at=2026-07-01T00:00:00Z")
            .body().contains("\"price\":12.0"));
        // before the first range: no applicable price
        assertEquals(404, getPrice("DRILL", "?currency=USD&at=2025-12-01T00:00:00Z")
            .statusCode());
    }

    @Test
    void defaultCurrencyFallbackApplies() throws Exception {
        assertEquals(201, postProduct("""
            {"productId":"SAW","productTypeId":"FINISHED_GOOD","internalName":"Saw",
             "prices":[
              {"productPriceTypeId":"DEFAULT_PRICE","currencyUomId":"USD",
               "fromDate":"2026-01-01T00:00:00Z","price":20.00}]}""").statusCode());

        HttpResponse<String> resolved = getPrice("SAW", "?currency=EUR&at=2026-02-01T00:00:00Z");
        assertEquals(200, resolved.statusCode());
        assertTrue(resolved.body().contains("\"currencyUomId\":\"USD\""));
    }

    @Test
    void priceUpdateEmitsPriceChangedEvent() throws Exception {
        assertEquals(201, postProduct("""
            {"productId":"HAMMER","productTypeId":"FINISHED_GOOD","internalName":"Hammer",
             "prices":[
              {"productPriceTypeId":"DEFAULT_PRICE","currencyUomId":"USD",
               "fromDate":"2026-01-01T00:00:00Z","price":5.00}]}""").statusCode());
        assertEquals(201, postProduct("""
            {"productId":"HAMMER","productTypeId":"FINISHED_GOOD","internalName":"Hammer",
             "prices":[
              {"productPriceTypeId":"DEFAULT_PRICE","currencyUomId":"USD",
               "fromDate":"2026-01-01T00:00:00Z","price":5.00},
              {"productPriceTypeId":"DEFAULT_PRICE","currencyUomId":"USD",
               "fromDate":"2026-08-01T00:00:00Z","price":6.00}]}""").statusCode());

        assertTrue(outbox.findAll().stream()
            .anyMatch(e -> "product.price.changed.v1".equals(e.getEventType())
                && "HAMMER".equals(e.getAggregateKey())
                && e.getPayload().contains("\"price\":6.00")));
    }

    @Test
    void snapshotAtOrderTimeIsImmutable() throws Exception {
        assertEquals(201, postProduct("""
            {"productId":"ANVIL","productTypeId":"FINISHED_GOOD","internalName":"Anvil",
             "prices":[
              {"productPriceTypeId":"DEFAULT_PRICE","currencyUomId":"USD",
               "fromDate":"2026-01-01T00:00:00Z","price":100.00}]}""").statusCode());

        // the order captures the price resolved at order time
        HttpResponse<String> snapshotAtOrder = getPrice("ANVIL",
            "?currency=USD&at=2026-03-01T00:00:00Z");
        assertEquals(200, snapshotAtOrder.statusCode());
        assertTrue(snapshotAtOrder.body().contains("\"price\":100.0"));

        // a later price rise does not change the captured snapshot ...
        assertEquals(201, postProduct("""
            {"productId":"ANVIL","productTypeId":"FINISHED_GOOD","internalName":"Anvil",
             "prices":[
              {"productPriceTypeId":"DEFAULT_PRICE","currencyUomId":"USD",
               "fromDate":"2026-01-01T00:00:00Z","price":100.00},
              {"productPriceTypeId":"DEFAULT_PRICE","currencyUomId":"USD",
               "fromDate":"2026-06-01T00:00:00Z","price":150.00}]}""").statusCode());

        // ... nor what the order instant resolves to after the update
        assertTrue(getPrice("ANVIL", "?currency=USD&at=2026-03-01T00:00:00Z")
            .body().contains("\"price\":100.0"));
        // the new price only applies from its own fromDate
        assertTrue(getPrice("ANVIL", "?currency=USD&at=2026-07-01T00:00:00Z")
            .body().contains("\"price\":150.0"));
    }
}
