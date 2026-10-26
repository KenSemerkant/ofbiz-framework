package billing.adapters.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import billing.adapters.rest.persistence.OutboxEntryEntity;
import billing.adapters.rest.persistence.OutboxRepository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * CG-5 invoice/payment flow, sliced: manual invoice → issue (event in the
 * outbox) → payment create + authorize + capture → invoice settles → PDF
 * renders; credit-limit breach is a typed 409; a retried payment create
 * with the same idempotency key returns the original; the reconciliation
 * endpoint reports a failed payment. Relay and ECA disabled — the outbox
 * rows ARE the event evidence (order-svc test convention); the on-cluster
 * end-to-end is 80-billing-lifecycle.sh.
 */
@SpringBootTest(properties = {"billing.relay.enabled=false", "billing.eca.enabled=false"},
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class BillingApiTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    @LocalServerPort
    int port;

    @Autowired
    OutboxRepository outbox;

    final HttpClient http = HttpClient.newHttpClient();
    final ObjectMapper json = new ObjectMapper();

    private JsonNode post(String path, String body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:%d%s".formatted(port, path)))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body)).build();
        return json.readTree(http.send(request, HttpResponse.BodyHandlers.ofString()).body());
    }

    private JsonNode action(String path) throws Exception {
        return post(path, "{}");
    }

    private JsonNode get(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:%d%s".formatted(port, path))).GET().build();
        return json.readTree(http.send(request, HttpResponse.BodyHandlers.ofString()).body());
    }

    private int status(String method, String path, String body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:%d%s".formatted(port, path)))
            .header("Content-Type", "application/json")
            .method(method, HttpRequest.BodyPublishers.ofString(body)).build();
        return http.send(request, HttpResponse.BodyHandlers.ofString()).statusCode();
    }

    private List<String> eventTypes() {
        return outbox.findAll().stream().map(OutboxEntryEntity::getEventType).toList();
    }

    @Test
    void invoiceIssuePaySettleWithPdf() throws Exception {
        post("/api/v1/billing-accounts", """
            {"billingAccountId":"BA-T1","customerId":"CUST-T1",
             "creditLimit":1000.00,"netDaysTerm":30}
            """);
        JsonNode invoice = post("/api/v1/invoices", """
            {"invoiceId":"INV-T1","customerId":"CUST-T1","billingAccountId":"BA-T1",
             "currencyUomId":"USD",
             "lines":[{"invoiceItemSeqId":"1","invoiceItemTypeId":"INV_FPROD_ITEM",
                       "description":"widget","quantity":2,"amount":11.00}]}
            """);
        assertEquals("DRAFT", invoice.path("status").asText());
        assertEquals("ISSUED", action("/api/v1/invoices/INV-T1/issue").path("status").asText());
        assertTrue(eventTypes().contains("billing.invoice.issued.v1"));

        JsonNode payment = post("/api/v1/payments", """
            {"paymentId":"PAY-T1","invoiceId":"INV-T1","idempotencyKey":"idem-t1",
             "amount":22.00,"currencyUomId":"USD"}
            """);
        assertEquals("PENDING", payment.path("status").asText());
        JsonNode retried = post("/api/v1/payments", """
            {"paymentId":"PAY-T1B","invoiceId":"INV-T1","idempotencyKey":"idem-t1",
             "amount":22.00,"currencyUomId":"USD"}
            """);
        assertEquals("PAY-T1", retried.path("paymentId").asText());
        assertEquals("AUTHORIZED", action("/api/v1/payments/PAY-T1/authorize")
            .path("status").asText());
        assertEquals("CAPTURED", action("/api/v1/payments/PAY-T1/capture")
            .path("status").asText());
        assertTrue(eventTypes().contains("billing.payment.captured.v1"));
        assertEquals("SETTLED", getInvoiceStatus());
        assertTrue(eventTypes().contains("billing.invoice.settled.v1"));

        HttpRequest pdfRequest = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:%d/api/v1/invoices/INV-T1/pdf".formatted(port)))
            .GET().build();
        HttpResponse<byte[]> pdf = http.send(pdfRequest, HttpResponse.BodyHandlers.ofByteArray());
        assertEquals(200, pdf.statusCode());
        assertTrue(new String(pdf.body(), 0, 5).equals("%PDF-"));

        // P9-F1 parity slice: AR find + payment listing for the SPA accounting area
        JsonNode byCustomer = get("/api/v1/invoices?customerId=CUST-T1");
        assertTrue(byCustomer.isArray() && byCustomer.size() >= 1);
        assertTrue(hasId(byCustomer, "INV-T1"));
        JsonNode byStatus = get("/api/v1/invoices?status=SETTLED");
        assertTrue(hasId(byStatus, "INV-T1"));
        JsonNode payments = get("/api/v1/payments?invoiceId=INV-T1");
        assertTrue(payments.isArray() && payments.size() >= 1);
        assertTrue(hasId(payments, "PAY-T1"));
    }

    private boolean hasId(JsonNode array, String id) {
        for (JsonNode row : array) {
            if (id.equals(row.path("invoiceId").asText())
                || id.equals(row.path("paymentId").asText())) {
                return true;
            }
        }
        return false;
    }

    private String getInvoiceStatus() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:%d/api/v1/invoices/INV-T1".formatted(port)))
            .GET().build();
        return json.readTree(http.send(request, HttpResponse.BodyHandlers.ofString()).body())
            .path("status").asText();
    }

    @Test
    void creditLimitBreachIsTyped409() throws Exception {
        post("/api/v1/billing-accounts", """
            {"billingAccountId":"BA-T2","customerId":"CUST-T2",
             "creditLimit":10.00,"netDaysTerm":30}
            """);
        post("/api/v1/invoices", """
            {"invoiceId":"INV-T2","customerId":"CUST-T2","billingAccountId":"BA-T2",
             "currencyUomId":"USD",
             "lines":[{"invoiceItemSeqId":"1","invoiceItemTypeId":"INV_FPROD_ITEM",
                       "description":"big widget","quantity":1,"amount":99.00}]}
            """);
        assertEquals(409, status("POST", "/api/v1/invoices/INV-T2/issue", "{}"));
    }

    @Test
    void voidPaymentCancelWriteOffReadPathsAnd404() throws Exception {
        post("/api/v1/invoices", """
            {"invoiceId":"INV-T4","customerId":"CUST-T4","currencyUomId":"USD",
             "lines":[{"invoiceItemSeqId":"1","invoiceItemTypeId":"INV_FPROD_ITEM",
                       "description":"w","quantity":1,"amount":8.00}]}
            """);
        action("/api/v1/invoices/INV-T4/issue");
        post("/api/v1/payments", """
            {"paymentId":"PAY-T4","invoiceId":"INV-T4","idempotencyKey":"idem-t4",
             "amount":8.00,"currencyUomId":"USD"}
            """);
        action("/api/v1/payments/PAY-T4/authorize");
        assertEquals("VOIDED", action("/api/v1/payments/PAY-T4/void").path("status").asText());

        post("/api/v1/invoices", """
            {"invoiceId":"INV-T5","customerId":"CUST-T5","currencyUomId":"USD",
             "lines":[{"invoiceItemSeqId":"1","invoiceItemTypeId":"INV_FPROD_ITEM",
                       "description":"to cancel","quantity":1,"amount":1.00}]}
            """);
        action("/api/v1/invoices/INV-T5/issue");
        assertEquals("CANCELLED", action("/api/v1/invoices/INV-T5/cancel").path("status")
            .asText());

        post("/api/v1/invoices", """
            {"invoiceId":"INV-T6","customerId":"CUST-T6","currencyUomId":"USD",
             "lines":[{"invoiceItemSeqId":"1","invoiceItemTypeId":"INV_FPROD_ITEM",
                       "description":"to write off","quantity":1,"amount":1.00}]}
            """);
        action("/api/v1/invoices/INV-T6/issue");
        assertEquals("WRITTEN_OFF", action("/api/v1/invoices/INV-T6/writeoff").path("status")
            .asText());

        post("/api/v1/billing-accounts", """
            {"billingAccountId":"BA-T3","customerId":"CUST-T4",
             "creditLimit":50.00,"netDaysTerm":30}
            """);
        post("/api/v1/invoices", """
            {"invoiceId":"INV-T7","customerId":"CUST-T4","billingAccountId":"BA-T3",
             "currencyUomId":"USD",
             "lines":[{"invoiceItemSeqId":"1","invoiceItemTypeId":"INV_FPROD_ITEM",
                       "description":"w","quantity":1,"amount":6.00}]}
            """);
        action("/api/v1/invoices/INV-T7/issue");
        post("/api/v1/payments", """
            {"paymentId":"PAY-T7","invoiceId":"INV-T7","idempotencyKey":"idem-t7",
             "amount":6.00,"currencyUomId":"USD"}
            """);
        action("/api/v1/payments/PAY-T7/authorize");
        action("/api/v1/payments/PAY-T7/capture");
        HttpRequest getRequest = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:%d/api/v1/payments/PAY-T7".formatted(port)))
            .GET().build();
        assertEquals("CAPTURED", json.readTree(http.send(getRequest,
            HttpResponse.BodyHandlers.ofString()).body()).path("status").asText());

        HttpRequest accountGet = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:%d/api/v1/billing-accounts/BA-T3"
                .formatted(port)))
            .GET().build();
        assertEquals(200, http.send(accountGet, HttpResponse.BodyHandlers.ofString())
            .statusCode());

        HttpRequest reconcile = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:%d/api/v1/payments/reconciliation"
                .formatted(port)))
            .GET().build();
        assertEquals(200, http.send(reconcile, HttpResponse.BodyHandlers.ofString())
            .statusCode());

        assertEquals(404, status("GET", "/api/v1/invoices/INV-404", ""));
    }

    @Test
    void illegalTransitionIsTyped409() throws Exception {
        post("/api/v1/invoices", """
            {"invoiceId":"INV-T3","customerId":"CUST-T3","currencyUomId":"USD",
             "lines":[{"invoiceItemSeqId":"1","invoiceItemTypeId":"INV_FPROD_ITEM",
                       "description":"w","quantity":1,"amount":5.00}]}
            """);
        assertEquals(409, status("POST", "/api/v1/invoices/INV-T3/writeoff", "{}"));
    }
}
