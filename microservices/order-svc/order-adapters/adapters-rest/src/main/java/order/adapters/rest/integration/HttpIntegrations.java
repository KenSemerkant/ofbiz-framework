package order.adapters.rest.integration;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import order.application.InventoryGateway;
import order.application.InventoryUnavailableException;
import order.application.PartyValidator;
import order.application.PriceSnapshotProvider;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * HTTP adapters for the saga's outbound ports: party validation
 * (party-svc), price snapshots (product-svc), reserve/release/receive
 * (inventory-svc). East-west calls go through the cluster (ADR-010);
 * base URLs are config so tests can point them at stubs.
 */
public final class HttpIntegrations {

    private static final ObjectMapper JSON = new ObjectMapper();

    private static final HttpClient CLIENT = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(3))
        .build();

    /**
     * Every saga-facing call is bounded (P4-F2-S1 fail-loud rule): a dead
     * neighbour surfaces as an exception after 5s, never a hang — the
     * placeOrder saga must reject, not park.
     */
    private static HttpRequest.Builder request(URI uri) {
        return HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(5));
    }

    @Component
    public static class HttpPartyValidator implements PartyValidator {

        private final String baseUrl;

        public HttpPartyValidator(@Value("${order.integration.party-base-url}") String baseUrl) {
            this.baseUrl = baseUrl;
        }

        @Override
        public boolean isValid(String partyId) {
            try {
                HttpResponse<String> response = CLIENT.send(
                    request(URI.create(baseUrl + "/api/v1/parties/" + partyId))
                        .GET().build(),
                    HttpResponse.BodyHandlers.ofString());
                return response.statusCode() >= 200 && response.statusCode() < 300;
            } catch (Exception e) {
                return false; // fail closed: no party proof, no order
            }
        }
    }

    @Component
    public static class HttpPriceSnapshotProvider implements PriceSnapshotProvider {

        private final String baseUrl;

        public HttpPriceSnapshotProvider(
                @Value("${order.integration.product-base-url}") String baseUrl) {
            this.baseUrl = baseUrl;
        }

        @Override
        public Optional<ResolvedPrice> resolve(String productId, String currencyUomId) {
            try {
                HttpResponse<String> response = CLIENT.send(
                    request(URI.create(baseUrl + "/api/v1/products/" + productId
                        + "/price?priceType=DEFAULT_PRICE&currency=" + currencyUomId
                        + "&at=" + java.time.Instant.now())).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() != 200) {
                    return Optional.empty();
                }
                JsonNode node = JSON.readTree(response.body());
                return Optional.of(new ResolvedPrice(
                    new BigDecimal(node.path("price").asText()),
                    node.path("currencyUomId").asText(currencyUomId)));
            } catch (Exception e) {
                return Optional.empty();
            }
        }
    }

    @Component
    public static class HttpInventoryGateway implements InventoryGateway {

        private final String baseUrl;

        public HttpInventoryGateway(
                @Value("${order.integration.inventory-base-url}") String baseUrl) {
            this.baseUrl = baseUrl;
        }

        @Override
        public ReservationReceipt reserve(String orderId, String orderItemSeqId,
                String productId, String facilityId, BigDecimal quantity) {
            String body = JSON.createObjectNode()
                .put("orderId", orderId)
                .put("orderItemSeqId", orderItemSeqId)
                .put("productId", productId)
                .put("facilityId", facilityId)
                .put("quantity", quantity)
                .toString();
            HttpResponse<String> response = post("/api/v1/reservations", body);
            if (response.statusCode() != 201 && response.statusCode() != 200) {
                // 409 conflict = insufficient stock, 404 = no item — both uncompensable
                throw new InventoryUnavailableException(
                    "inventory rejected reserve for " + productId + ": " + response.statusCode());
            }
            JsonNode node = JSON.readTree(response.body());
            return new ReservationReceipt(node.path("reservationId").asText(),
                node.path("inventoryItemId").asText(), productId, facilityId);
        }

        @Override
        public void release(String reservationId) {
            post("/api/v1/reservations/" + reservationId + "/cancel", "{}");
        }

        @Override
        public void receive(String inventoryItemId, BigDecimal quantity) {
            HttpResponse<String> response = post(
                "/api/v1/inventory-items/" + inventoryItemId + "/receive",
                JSON.createObjectNode().put("quantity", quantity).toString());
            if (response.statusCode() >= 300) {
                throw new IllegalStateException(
                    "inventory receive failed " + response.statusCode());
            }
        }

        private HttpResponse<String> post(String path, String body) {
            try {
                return CLIENT.send(request(URI.create(baseUrl + path))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build(),
                    HttpResponse.BodyHandlers.ofString());
            } catch (Exception e) {
                throw new IllegalStateException("inventory call failed: " + e.getMessage(), e);
            }
        }
    }

    private HttpIntegrations() {
    }
}
