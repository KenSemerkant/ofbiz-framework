package gateway.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * P1-F1-S1 remaining AC: a token from the issuer's live JWKS endpoint
 * validates at the gateway. Unlike {@link GatewayRoutingTest}, no test
 * decoder bean is registered — the decoder is Boot's auto-configured one,
 * built from {@code spring.security.oauth2.resourceserver.jwt.jwk-set-uri},
 * i.e. the production wiring. A token signed by a key the JWKS does not
 * publish must still be rejected, proving validation is real.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayLiveJwksTest {

    private static final KeyPair ISSUER_KEY = rsaKeyPair();
    private static final KeyPair STRANGER_KEY = rsaKeyPair();
    private static final String KID = "live-jwks-test-key";

    @Container
    static final GenericContainer<?> REDIS =
        new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    private static final HttpServer JWKS = jwksServer();
    private static final HttpServer DEMO = bodyServer("demo-ok");
    private static final AtomicInteger DEMO_HITS = new AtomicInteger();

    private final HttpClient http = HttpClient.newHttpClient();

    @Value("${local.server.port}")
    private int port;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("gateway.demo.uri", () -> "http://127.0.0.1:" + DEMO.getAddress().getPort());
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
            () -> "http://127.0.0.1:" + JWKS.getAddress().getPort() + "/oauth2/jwks");
        // Boot pairs the jwk-set-uri decoder with an issuer validator when
        // issuer-uri is set (the dev profile default), so pin it to the token's iss.
        registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri", () -> "http://identity-svc");
    }

    @AfterAll
    static void stopStubs() {
        JWKS.stop(0);
        DEMO.stop(0);
    }

    @Test
    void issuerTokenFromLiveJwksIsAccepted() throws Exception {
        HttpResponse<String> response = get("/api/v1/demo/hello",
            rs256Token(ISSUER_KEY, "live-jwks-subject", Instant.now().plusSeconds(60)));
        assertEquals(200, response.statusCode(), response.body());
        assertEquals("demo-ok", response.body());
        assertEquals(1, DEMO_HITS.get(), "demo-svc stub was never called");
    }

    @Test
    void tokenSignedByUnknownKeyIsRejected() throws Exception {
        assertEquals(401, get("/api/v1/demo/hello",
            rs256Token(STRANGER_KEY, "stranger-subject", Instant.now().plusSeconds(60))).statusCode());
    }

    @Test
    void expiredIssuerTokenIsRejected() throws Exception {
        assertEquals(401, get("/api/v1/demo/hello",
            rs256Token(ISSUER_KEY, "expired-subject", Instant.now().minusSeconds(120))).statusCode());
    }

    private HttpResponse<String> get(String path, String token) throws Exception {
        return http.send(
            HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Authorization", "Bearer " + token)
                .GET().build(),
            HttpResponse.BodyHandlers.ofString());
    }

    /** Mints an RS256 JWT the way the issuer does: header.kid + claims + SHA256withRSA. */
    static String rs256Token(KeyPair key, String subject, Instant expiresAt) throws Exception {
        String header = base64("{\"alg\":\"RS256\",\"typ\":\"JWT\",\"kid\":\"" + KID + "\"}");
        String payload = base64("{\"sub\":\"" + subject + "\",\"iss\":\"http://identity-svc\","
            + "\"iat\":" + Instant.now().getEpochSecond() + ","
            + "\"exp\":" + expiresAt.getEpochSecond() + "}");
        String signingInput = header + "." + payload;
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(key.getPrivate());
        signature.update(signingInput.getBytes(StandardCharsets.US_ASCII));
        return signingInput + "." + Base64.getUrlEncoder().withoutPadding()
            .encodeToString(signature.sign());
    }

    private static HttpServer jwksServer() {
        try {
            RSAPublicKey publicKey = (RSAPublicKey) ISSUER_KEY.getPublic();
            String jwks = "{\"keys\":[{"
                + "\"kty\":\"RSA\",\"use\":\"sig\",\"alg\":\"RS256\",\"kid\":\"" + KID + "\","
                + "\"n\":\"" + b64Unsigned(publicKey.getModulus().toByteArray()) + "\","
                + "\"e\":\"" + b64Unsigned(publicKey.getPublicExponent().toByteArray()) + "\"}]}";
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/oauth2/jwks", exchange -> {
                byte[] bytes = jwks.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
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

    private static HttpServer bodyServer(String body) {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", exchange -> {
                DEMO_HITS.incrementAndGet();
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

    private static KeyPair rsaKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** JWK integers are unsigned: drop BigInteger's sign byte before encoding. */
    private static String b64Unsigned(byte[] bigEndian) {
        int start = bigEndian.length > 1 && bigEndian[0] == 0 ? 1 : 0;
        return Base64.getUrlEncoder().withoutPadding()
            .encodeToString(java.util.Arrays.copyOfRange(bigEndian, start, bigEndian.length));
    }

    private static String base64(String value) {
        return Base64.getUrlEncoder().withoutPadding()
            .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
}
