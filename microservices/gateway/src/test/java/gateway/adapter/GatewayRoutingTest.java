package gateway.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
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
 * P1-F2 acceptance: JWT enforcement, demo routing, 404 for unknown contexts,
 * Redis rate limiting with Retry-After, monolith bridge identity/host/trace
 * propagation, CORS and security headers.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayRoutingTest {

    private static final byte[] SECRET = "test-gateway-hmac-secret-32bytes!!".getBytes(StandardCharsets.UTF_8);

    @Container
    static final GenericContainer<?> REDIS =
        new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    private static final HttpServer DEMO = server("demo-ok");
    private static final HttpServer MONOLITH = server("monolith-ok");
    private static final List<Map<String, List<String>>> DEMO_REQUESTS = new CopyOnWriteArrayList<>();
    private static final List<Map<String, List<String>>> MONOLITH_REQUESTS = new CopyOnWriteArrayList<>();

    private final HttpClient http = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NEVER).build();

    @Value("${local.server.port}")
    private int port;

    private static HttpServer server(String body) {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", exchange -> {
                Map<String, List<String>> headers = new java.util.TreeMap<>(String.CASE_INSENSITIVE_ORDER);
                exchange.getRequestHeaders().forEach((k, v) -> headers.put(k, new ArrayList<>(v)));
                (body.startsWith("demo") ? DEMO_REQUESTS : MONOLITH_REQUESTS).add(headers);
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
    static void stopStubs() {
        DEMO.stop(0);
        MONOLITH.stop(0);
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("gateway.demo.uri", () -> "http://127.0.0.1:" + DEMO.getAddress().getPort());
        registry.add("gateway.monolith.uri", () -> "http://127.0.0.1:" + MONOLITH.getAddress().getPort());
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("gateway.monolith.host-header", () -> "ofbiz-monolith.ofbiz.svc");
    }

    @TestConfiguration
    static class JwtTestConfig {
        @Bean
        ReactiveJwtDecoder reactiveJwtDecoder() {
            return NimbusReactiveJwtDecoder.withSecretKey(new SecretKeySpec(SECRET, "HmacSHA256"))
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        }
    }

    @Test
    void absentTokenIsRejected() throws Exception {
        assertEquals(401, get("/api/v1/demo/hello", null, Map.of()).statusCode());
    }

    @Test
    void expiredTokenIsRejected() throws Exception {
        String expired = token("expired-subject", Instant.now().minusSeconds(120));
        assertEquals(401, get("/api/v1/demo/hello", expired, Map.of()).statusCode());
    }

    @Test
    void demoRouteForwardsAuthenticatedRequest() throws Exception {
        HttpResponse<String> response = get("/api/v1/demo/hello", token("alice", Instant.now().plusSeconds(60)), Map.of());
        assertEquals(200, response.statusCode(), response.body());
        assertEquals("demo-ok", response.body());
        assertTrue(!DEMO_REQUESTS.isEmpty(), "demo-svc stub was never called");
    }

    @Test
    void unknownContextReturns404() throws Exception {
        assertEquals(404, get("/api/v1/unknown/thing",
            token("bob", Instant.now().plusSeconds(60)), Map.of()).statusCode());
    }

    @Test
    void burstGets429WithRetryAfter() throws Exception {
        String token = token("burst-subject", Instant.now().plusSeconds(120));
        List<Integer> statuses = new ArrayList<>();
        String retryAfter = null;
        for (int i = 0; i < 8; i++) {
            HttpResponse<String> response = get("/api/v1/demo/hello", token, Map.of());
            statuses.add(response.statusCode());
            if (response.statusCode() == 429) {
                retryAfter = response.headers().firstValue("Retry-After").orElse(null);
            }
        }
        assertTrue(statuses.contains(429), "rate limit never fired: " + statuses);
        assertNotNull(retryAfter, "429 must carry Retry-After");
        assertTrue(retryAfter.matches("\\d+"), "Retry-After must be seconds: " + retryAfter);
    }

    @Test
    void monolithBridgeForwardsIdentityHostAndTraceParent() throws Exception {
        String traceParent = "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01";
        HttpResponse<String> response = get("/webtools/control/main",
            token("carol", Instant.now().plusSeconds(60)), Map.of("traceparent", traceParent));

        assertEquals(200, response.statusCode(), response.body());
        assertEquals("monolith-ok", response.body());
        Map<String, List<String>> seen = MONOLITH_REQUESTS.get(MONOLITH_REQUESTS.size() - 1);
        assertEquals("carol", first(seen, "X-Auth-User"));
        assertEquals("ofbiz-monolith.ofbiz.svc", first(seen, "Host"));
        assertEquals(traceParent, first(seen, "traceparent"));
    }

    @Test
    void corsPreflightAndSecurityHeaders() throws Exception {
        HttpResponse<String> preflight = http.send(
            HttpRequest.newBuilder(URI.create(base() + "/api/v1/demo/hello"))
                .header("Origin", "http://localhost:4200")
                .header("Access-Control-Request-Method", "GET")
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                .build(),
            HttpResponse.BodyHandlers.ofString());
        assertEquals(200, preflight.statusCode());
        assertEquals("http://localhost:4200",
            preflight.headers().firstValue("Access-Control-Allow-Origin").orElse(null));

        HttpResponse<String> response = get("/api/v1/demo/hello",
            token("dave", Instant.now().plusSeconds(60)), Map.of("Origin", "http://localhost:4200"));
        assertEquals("nosniff", response.headers().firstValue("X-Content-Type-Options").orElse(null));
        assertNotNull(response.headers().firstValue("Strict-Transport-Security").orElse(null),
            "HSTS header missing");
        assertNotNull(response.headers().firstValue("Content-Security-Policy").orElse(null),
            "CSP header missing");
    }

    private String base() {
        return "http://localhost:" + port;
    }

    private HttpResponse<String> get(String path, String token, Map<String, String> headers)
            throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(base() + path)).GET();
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        headers.forEach(request::header);
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static String first(Map<String, List<String>> headers, String name) {
        List<String> values = headers.get(name);
        return values == null || values.isEmpty() ? null : values.get(0);
    }

    /** Mints an HS256 JWT accepted by the test decoder. */
    static String token(String subject, Instant expiresAt) throws Exception {
        String header = base64("{\"alg\":\"HS256\",\"typ\":\"JWT\"}");
        String payload = base64("{\"sub\":\"" + subject + "\",\"iss\":\"gateway-test\","
            + "\"iat\":" + Instant.now().getEpochSecond() + ","
            + "\"exp\":" + expiresAt.getEpochSecond() + "}");
        String signingInput = header + "." + payload;
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET, "HmacSHA256"));
        return signingInput + "." + Base64.getUrlEncoder().withoutPadding()
            .encodeToString(mac.doFinal(signingInput.getBytes(StandardCharsets.US_ASCII)));
    }

    private static String base64(String value) {
        return Base64.getUrlEncoder().withoutPadding()
            .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
}
