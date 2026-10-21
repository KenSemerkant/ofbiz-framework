package gateway.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * P2-F3-S1 AC: the party route (metadata required-role=CUSTOMER) returns
 * 403 for a token without the role and 200 with it.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayRoleAuthTest {

    private static final byte[] SECRET =
        "test-gateway-hmac-secret-32bytes!!".getBytes(StandardCharsets.UTF_8);

    @Container
    static final GenericContainer<?> REDIS =
        new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    private static final HttpServer PARTY = stub("party-ok");

    private final HttpClient http = HttpClient.newHttpClient();

    @Value("${local.server.port}")
    private int port;

    private static HttpServer stub(String body) {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", exchange -> {
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, bytes.length);
                exchange.getResponseBody().write(bytes);
                exchange.close();
            });
            server.start();
            return server;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @AfterAll
    static void stopStub() {
        PARTY.stop(0);
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("gateway.party.uri", () -> "http://127.0.0.1:" + PARTY.getAddress().getPort());
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @TestConfiguration
    static class JwtTestConfig {
        @Bean
        ReactiveJwtDecoder reactiveJwtDecoder() {
            return NimbusReactiveJwtDecoder.withSecretKey(
                    new SecretKeySpec(SECRET, "HmacSHA256"))
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        }
    }

    private static String token(String subject, List<String> roles) throws Exception {
        String header = base64("{\"alg\":\"HS256\",\"typ\":\"JWT\"}");
        String payload = base64("{\"sub\":\"" + subject + "\",\"iss\":\"gateway-test\","
            + "\"iat\":" + Instant.now().getEpochSecond() + ","
            + "\"exp\":" + Instant.now().plusSeconds(120).getEpochSecond()
            + (roles == null ? "" : ",\"roles\":" + toJsonArray(roles)) + "}");
        String signingInput = header + "." + payload;
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET, "HmacSHA256"));
        return signingInput + "." + Base64.getUrlEncoder().withoutPadding()
            .encodeToString(mac.doFinal(signingInput.getBytes(StandardCharsets.US_ASCII)));
    }

    private static String toJsonArray(List<String> values) {
        StringBuilder json = new StringBuilder("[");
        for (String value : values) {
            if (json.length() > 1) {
                json.append(',');
            }
            json.append('"').append(value).append('"');
        }
        return json.append(']').toString();
    }

    private static String base64(String value) {
        return Base64.getUrlEncoder().withoutPadding()
            .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private HttpResponse<String> get(String path, String token) throws Exception {
        return http.send(HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + port + path))
            .header("Authorization", "Bearer " + token)
            .GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void partyRouteAdmitsTokenWithRole() throws Exception {
        HttpResponse<String> response =
            get("/api/v1/parties/P1", token("ada", List.of("USER", "CUSTOMER")));
        assertEquals(200, response.statusCode(), response.body());
        assertEquals("party-ok", response.body());
    }

    @Test
    void partyRouteReturns403WithoutRole() throws Exception {
        assertEquals(403, get("/api/v1/parties/P1", token("bob", List.of("USER"))).statusCode());
        // and a token with no roles claim at all
        assertEquals(403, get("/api/v1/parties/P1", token("carol", null)).statusCode());
    }
}
