package order.adapters.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import order.adapters.rest.events.MonolithSyncConsumers;
import order.adapters.rest.events.OrderConsumers;
import order.adapters.rest.events.OutboxRelay;
import order.adapters.rest.events.SagaResumeRunner;
import order.adapters.rest.integration.HttpIntegrations;
import order.adapters.rest.persistence.JacksonOrderSerializer;
import order.adapters.rest.persistence.JdbcMonolithOrderSyncWriter;
import order.adapters.rest.persistence.JdbcSagaStore;
import order.adapters.rest.persistence.OutboxEntryEntity;
import order.adapters.rest.persistence.OutboxRepository;
import order.adapters.rest.persistence.SmallStores;
import order.application.AttributionService;
import order.application.AttributionStore;
import order.application.InventoryGateway;
import order.application.InventoryUnavailableException;
import order.application.OrderEcaService;
import order.application.OrderLifecycleService;
import order.application.OrderSerializer;
import order.domain.Order;
import order.domain.OrderLine;
import order.domain.OrderStatus;
import order.domain.OrderStatusChange;

/**
 * Unit coverage for the adapter classes the API slice leaves cold (relay,
 * consumers, HTTP integrations, JDBC stores) — product-svc's
 * ProductAdaptersUnitTest pattern: fakes + one Postgres container.
 */
@Testcontainers
class OrderAdaptersUnitTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    static JdbcTemplate db;

    @BeforeAll
    static void schema() {
        db = new JdbcTemplate(new org.springframework.jdbc.datasource.DriverManagerDataSource(
            POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
        db.execute("""
            create table saga_state (order_id varchar(255) primary key,
                step varchar(32) not null, reservations jsonb not null default '[]',
                updated_at timestamptz not null default now())
            """);
        db.execute("create table processed_events (event_id varchar(255) primary key,"
            + " processed_at timestamptz not null default now())");
        db.execute("""
            create table monolith_order_projection (event_id varchar(255) primary key,
                topic varchar(128) not null, payload text not null,
                applied_at timestamptz not null default now())
            """);
        db.execute("""
            create table "ORDER_HEADER" ("ORDER_ID" varchar(255) primary key,
                "CUSTOMER_ID" varchar(255) not null, "STATUS_ID" varchar(32) not null,
                "CURRENCY_UOM" varchar(20) not null, "GRAND_TOTAL" numeric(18,2) not null)
            """);
        db.execute("""
            create table "ORDER_ITEM" ("ORDER_ID" varchar(255) not null,
                "ORDER_ITEM_SEQ_ID" varchar(20) not null, "PRODUCT_ID" varchar(255) not null,
                "QUANTITY" numeric(18,3) not null, "UNIT_PRICE" numeric(18,2) not null,
                "UNIT_LIST_PRICE" numeric(18,2) not null,
                primary key ("ORDER_ID", "ORDER_ITEM_SEQ_ID"))
            """);
        db.execute("""
            create table "ORDER_STATUS_HISTORY" ("ORDER_ID" varchar(255) not null,
                "STATUS_ID" varchar(32) not null, "NOTE" varchar(255),
                "CHANGED_AT" timestamptz not null default now())
            """);
    }

    // ---- saga store markers (guarded SQL on real Postgres) ----

    @Test
    void sagaStoreMarkersAreGuarded() {
        JdbcSagaStore sagas = new JdbcSagaStore(db);
        sagas.create("ORDER-U1");
        assertTrue(sagas.markReserving("ORDER-U1"));
        assertFalse(sagas.markReserving("ORDER-U1"), "second marker advance must not win");
        sagas.storeReservations("ORDER-U1", List.of(new InventoryGateway.ReservationReceipt(
            "RES-1", "INV-1", "PROD-1", "WH")));
        assertTrue(sagas.markInvoiced("ORDER-U1"));
        assertFalse(sagas.markInvoiced("ORDER-U1"));
        sagas.markDone("ORDER-U1");
        assertTrue(sagas.find("ORDER-U1").isPresent());
        assertTrue(sagas.find("missing").isEmpty());
        assertEquals(0, sagas.findIncompleteOrderIds().size());
        sagas.create("ORDER-U2");
        assertEquals(List.of("ORDER-U2"), sagas.findIncompleteOrderIds());
        sagas.markCompensated("ORDER-U2");
        assertEquals(0, sagas.findIncompleteOrderIds().size());
    }

    @Test
    void processedEventStoreIsIdempotent() {
        SmallStores.JdbcProcessedEventStore processed =
            new SmallStores.JdbcProcessedEventStore(db);
        assertTrue(processed.recordIfAbsent("evt-1"));
        assertFalse(processed.recordIfAbsent("evt-1"), "a replayed id is a no-op");
    }

    // ---- monolith writers + CDC projection (real SQL) ----

    @Test
    void monolithOrderWriterUpsertsAndAppendsHistory() {
        JdbcMonolithOrderSyncWriter writer = new JdbcMonolithOrderSyncWriter(db);
        writer.upsertHeader("ORDER-M1", "CUST-1", "CREATED", "USD", "19.98");
        writer.upsertHeader("ORDER-M1", "CUST-1", "APPROVED", "USD", "19.98");
        assertEquals("APPROVED", db.queryForObject(
            "select \"STATUS_ID\" from \"ORDER_HEADER\" where \"ORDER_ID\" = 'ORDER-M1'",
            String.class));
        writer.upsertItems("ORDER-M1", List.of(
            new order.adapters.rest.persistence.OrderJson.LineDto("00001", "PROD-1", "WH",
                BigDecimal.ONE, new BigDecimal("9.99"), "USD")));
        writer.upsertItems("ORDER-M1", List.of(
            new order.adapters.rest.persistence.OrderJson.LineDto("00001", "PROD-1", "WH",
                new BigDecimal("2"), new BigDecimal("9.99"), "USD")));
        assertEquals(0, new BigDecimal("2").compareTo(db.queryForObject(
            "select \"QUANTITY\" from \"ORDER_ITEM\" where \"ORDER_ID\" = 'ORDER-M1'", BigDecimal.class)));
        writer.appendHistory("ORDER-M1", "APPROVED", "approved");
        assertEquals(1, db.queryForObject(
            "select count(*) from \"ORDER_STATUS_HISTORY\" where \"ORDER_ID\" = 'ORDER-M1'",
            Integer.class));
    }

    @Test
    void cdcProjectionStoresEachEventOnce() {
        MonolithSyncConsumers.CdcProjection projection =
            new MonolithSyncConsumers.CdcProjection(db);
        ConsumerRecord<String, String> record = new ConsumerRecord<>("ofbiz.ORDER_HEADER", 0,
            5, "ORDER-M1", "{\"after\":{\"ORDER_ID\":\"ORDER-M1\"}}");
        projection.onCdcEvent(record);
        projection.onCdcEvent(record);
        assertEquals(1, db.queryForObject(
            "select count(*) from monolith_order_projection", Integer.class));
    }

    @Test
    void reverseSyncConsumerWritesHeaderItemsHistory() {
        JdbcMonolithOrderSyncWriter writer = new JdbcMonolithOrderSyncWriter(db);
        MonolithSyncConsumers.ReverseSync consumer = new MonolithSyncConsumers.ReverseSync(writer);
        consumer.onOrderEvent("""
            {"orderId":"ORDER-M2","customerId":"CUST-2","grandTotal":"9.99","currency":"USD",
             "lines":[{"orderItemSeqId":"00001","productId":"PROD-1","facilityId":"WH",
                       "quantity":1,"unitPrice":9.99,"currencyUomId":"USD"}]}
            """);
        consumer.onOrderEvent(
            "{\"orderId\":\"ORDER-M2\",\"customerId\":\"CUST-2\",\"status\":\"APPROVED\","
                + "\"grandTotal\":\"9.99\",\"currency\":\"USD\",\"note\":\"approved\"}");
        assertEquals("APPROVED", db.queryForObject(
            "select \"STATUS_ID\" from \"ORDER_HEADER\" where \"ORDER_ID\" = 'ORDER-M2'", String.class));
        assertEquals(2, db.queryForObject(
            "select count(*) from \"ORDER_STATUS_HISTORY\" where \"ORDER_ID\" = 'ORDER-M2'",
            Integer.class));
    }

    // ---- outbox relay ----

    @Test
    void relayPublishesAndMarks() {
        OutboxEntryEntity entry = new OutboxEntryEntity("order.order.placed.v1", "ORDER-R",
            "{}");
        OutboxRepository repo = org.mockito.Mockito.mock(OutboxRepository.class);
        org.mockito.Mockito.when(repo.findUnpublished()).thenReturn(List.of(entry));
        org.mockito.Mockito.when(repo.findById(org.mockito.ArgumentMatchers.any()))
            .thenReturn(java.util.Optional.of(entry));
        @SuppressWarnings("unchecked")
        org.springframework.kafka.core.KafkaTemplate<String, String> kafka =
            org.mockito.Mockito.mock(org.springframework.kafka.core.KafkaTemplate.class);
        org.mockito.Mockito
            .when(kafka.send("order.order.placed.v1", "ORDER-R", "{}"))
            .thenReturn(java.util.concurrent.CompletableFuture.completedFuture(null));
        OutboxRelay relay = new OutboxRelay(repo, kafka,
            new TransactionTemplate(new NoopTransactionManager()));
        relay.publishPending();
        org.mockito.Mockito.verify(kafka).send("order.order.placed.v1", "ORDER-R", "{}");
        assertNotNull(entry.getPublishedAt(), "relay must mark the row published");
    }

    static class NoopTransactionManager implements PlatformTransactionManager {
        @Override
        public TransactionStatus getTransaction(TransactionDefinition definition) {
            return new SimpleTransactionStatus();
        }

        @Override
        public void commit(TransactionStatus status) {
        }

        @Override
        public void rollback(TransactionStatus status) {
        }
    }

    // ---- ECA consumers ----

    @Test
    void placedConsumerDrivesEcaAndSkipsReplays() {
        List<String> ecaCalls = new ArrayList<>();
        List<String> cancels = new ArrayList<>();
        AtomicInteger processed = new AtomicInteger();
        OrderConsumers consumers = new OrderConsumers(
            new OrderEcaService(null, null, null, null, null) {
                @Override
                public void onPlaced(String orderId) {
                    ecaCalls.add(orderId);
                }

                @Override
                public void compensate(String orderId) {
                    ecaCalls.add("compensate:" + orderId);
                }
            },
            new OrderLifecycleService(null, null, null) {
                @Override
                public Order cancel(String orderId, String note) {
                    cancels.add(orderId + ":" + note);
                    return null;
                }
            },
            null,
            eventId -> processed.incrementAndGet() == 1);

        ConsumerRecord<String, String> record = new ConsumerRecord<>("order.order.placed.v1",
            0, 0, "ORDER-C1", "{\"orderId\":\"ORDER-C1\"}");
        consumers.onPlaced(record);
        consumers.onPlaced(record); // replay skipped
        assertEquals(List.of("ORDER-C1"), ecaCalls);
        assertEquals(0, cancels.size());
    }

    @Test
    void placedConsumerCancelsOnInsufficientStock() {
        List<String> cancels = new ArrayList<>();
        OrderConsumers consumers = new OrderConsumers(
            new OrderEcaService(null, null, null, null, null) {
                @Override
                public void onPlaced(String orderId) {
                    throw new InventoryUnavailableException("no ATP");
                }
            },
            new OrderLifecycleService(null, null, null) {
                @Override
                public Order cancel(String orderId, String note) {
                    cancels.add(orderId);
                    return null;
                }
            },
            null, eventId -> true);
        consumers.onPlaced(new ConsumerRecord<>("order.order.placed.v1", 0, 0, "k",
            "{\"orderId\":\"ORDER-C2\"}"));
        assertEquals(List.of("ORDER-C2"), cancels,
                "insufficient stock must cancel with the typed event");
    }

    @Test
    void attributionConsumerRecordsAndSkipsReplays() {
        List<String> rows = new ArrayList<>();
        OrderConsumers consumers = new OrderConsumers(null, null,
            new AttributionService(new AttributionStore() {
                @Override
                public void record(Attribution attribution) {
                    rows.add(attribution.trackingCode());
                }

                @Override
                public long countByTrackingCode(String trackingCode) {
                    return 0;
                }
            }),
            eventId -> rows.isEmpty());
        String payload = "{\"orderId\":\"O\",\"customerId\":\"C\",\"trackingCode\":"
            + "\"PROMO-42\"}";
        consumers.onPlacedAttribution(new ConsumerRecord<>("order.order.placed.v1", 0, 0, "k",
            payload));
        consumers.onPlacedAttribution(new ConsumerRecord<>("order.order.placed.v1", 0, 1, "k",
            payload));
        assertEquals(List.of("PROMO-42"), rows);
        // no tracking code → nothing recorded
        consumers.onPlacedAttribution(new ConsumerRecord<>("order.order.placed.v1", 0, 2, "k",
            "{\"orderId\":\"O\",\"customerId\":\"C\"}"));
        assertEquals(1, rows.size());
    }

    @Test
    void cancelledConsumerCompensates() {
        List<String> compensated = new ArrayList<>();
        OrderConsumers consumers = new OrderConsumers(
            new OrderEcaService(null, null, null, null, null) {
                @Override
                public void compensate(String orderId) {
                    compensated.add(orderId);
                }
            }, null, null, eventId -> true);
        consumers.onCancelled(new ConsumerRecord<>("order.order.cancelled.v1", 0, 0, "k",
            "{\"orderId\":\"ORDER-C3\"}"));
        assertEquals(List.of("ORDER-C3"), compensated);
    }

    @Test
    void resumeRunnerDrivesPendingSagas() throws Exception {
        List<String> resumed = new ArrayList<>();
        new SagaResumeRunner(new OrderEcaService(null, null, null, null, null) {
            @Override
            public void resumePending() {
                resumed.add("run");
            }
        }).run(null);
        assertEquals(List.of("run"), resumed);
    }

    // ---- serializer corners ----

    @Test
    void serializerCorners() {
        Order order = Order.rehydrate("ORDER-S1", "CUST-1", null,
            List.of(new OrderLine("00001", "PROD-1", "WH", BigDecimal.ONE,
                new BigDecimal("9.99"), "USD")),
            OrderStatus.CREATED,
            List.of(new OrderStatusChange(OrderStatus.CREATED, Instant.now(), "placed")));
        OrderSerializer serializer = new JacksonOrderSerializer();
        assertTrue(serializer.serializeOrder(order).contains("ORDER-S1"));
        assertTrue(serializer.serializeLineNode(order.lines()).contains("PROD-1"));
        assertTrue(serializer.serializeStatusChange(order, "note").contains("CREATED"));
        assertTrue(serializer.serializeInvoiceRequest(order).contains("9.99"));
        assertTrue(serializer.serializePlaced(order).contains("\"lineCount\":1"));
    }

    // ---- HTTP integrations against a stub server ----

    static HttpServer server;
    static int port;

    @BeforeAll
    static void stubUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/api/v1/parties/CUST-1", exchange -> {
            respond(exchange, 200, "{}");
        });
        server.createContext("/api/v1/parties/GHOST", exchange -> {
            respond(exchange, 404, "{}");
        });
        server.createContext("/api/v1/products/PROD-1/price", exchange -> {
            if (exchange.getRequestURI().getQuery().contains("currency=EUR")) {
                respond(exchange, 404, "{}");
                return;
            }
            respond(exchange, 200,
                "{\"productId\":\"PROD-1\",\"price\":9.99,\"currencyUomId\":\"USD\"}");
        });
        server.createContext("/api/v1/products/PROD-X/price", exchange -> {
            respond(exchange, 404, "{}");
        });
        server.createContext("/api/v1/reservations", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes());
            if (body.contains("PROD-OUT")) {
                respond(exchange, 409, "{}");
                return;
            }
            respond(exchange, 201,
                "{\"reservationId\":\"RES-1\",\"inventoryItemId\":\"INV-1\","
                    + "\"status\":\"CREATED\"}");
        });
        server.createContext("/api/v1/inventory-items/INV-1/receive", exchange -> {
            respond(exchange, 200, "{}");
        });
        server.createContext("/api/v1/inventory-items/INV-9/receive", exchange -> {
            respond(exchange, 500, "{}");
        });
        server.start();
        port = server.getAddress().getPort();
    }

    @AfterAll
    static void stubDown() {
        server.stop(0);
    }

    static void respond(com.sun.net.httpserver.HttpExchange exchange, int status, String body) {
        try (OutputStream out = exchange.getResponseBody()) {
            byte[] bytes = body.getBytes();
            exchange.sendResponseHeaders(status, bytes.length);
            out.write(bytes);
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void monolithSyncConfigBuildsBeans() {
        var config = new order.adapters.rest.config.MonolithSyncConfig();
        var ds = config.orderDataSource("jdbc:postgresql://localhost/x", "u", "p");
        assertNotNull(ds);
        var jdbc = config.monolithJdbc(ds);
        assertNotNull(jdbc);
        // the service-own JdbcTemplate must exist too, or the CDC projection
        // would fall back to the monolith one (party-svc P2 lesson)
        assertNotNull(config.orderJdbc(ds));
    }

    @Test
    void entityGettersStayCovered() {
        var order = new order.adapters.rest.persistence.OrderEntity("ORDER-E", "CUST-1",
            "PROMO", "CREATED", "[]", "[]", BigDecimal.TEN, "USD", Instant.now());
        assertEquals("ORDER-E", order.getOrderId());
        assertEquals("CUST-1", order.getCustomerId());
        assertEquals("PROMO", order.getTrackingCode());
        assertEquals("CREATED", order.getStatus());
        assertEquals("[]", order.getLines());
        assertEquals("[]", order.getHistory());
        assertEquals(0, BigDecimal.TEN.compareTo(order.getGrandTotal()));
        assertEquals("USD", order.getCurrencyUomId());
        assertNotNull(order.getPlacedAt());
        assertNotNull(order.getUpdatedAt());

        var quote = new order.adapters.rest.persistence.QuoteEntity("Q", "CUST-1", "DRAFT",
            "[]");
        assertEquals("Q", quote.getQuoteId());
        assertEquals("DRAFT", quote.getStatus());
        assertEquals("[]", quote.getLines());

        var returns = new order.adapters.rest.persistence.ReturnEntity("R", "ORDER-E",
            "COMPLETED", "[]");
        assertEquals("R", returns.getReturnId());
        assertEquals("ORDER-E", returns.getOrderId());
        assertEquals("COMPLETED", returns.getStatus());
        assertEquals("[]", returns.getLines());

        var outbox = new OutboxEntryEntity("t", "k", "{}");
        assertNotNull(outbox.getOccurredAt());
        // publishedAt stays null until the relay marks it

        var attribution = new SmallStores.AttributionEntity("ORDER-E", "CUST-1", "PROMO-42",
            Instant.now());
        assertEquals("PROMO-42", attribution.getTrackingCode());
    }

    @Test
    void httpFailurePathsSurfaceAsTypedErrors() throws Exception {
        // a closed port: connect refused → the adapters must not swallow it
        HttpServer probe = HttpServer.create(new InetSocketAddress(0), 0);
        int deadPort = probe.getAddress().getPort();
        probe.stop(0);

        var validator = new HttpIntegrations.HttpPartyValidator("http://localhost:" + deadPort);
        assertFalse(validator.isValid("CUST-1"), "connection failure must fail closed");

        var prices = new HttpIntegrations.HttpPriceSnapshotProvider(
            "http://localhost:" + deadPort);
        assertTrue(prices.resolve("PROD-1", "USD").isEmpty());

        var inventory = new HttpIntegrations.HttpInventoryGateway(
            "http://localhost:" + deadPort);
        assertThrows(IllegalStateException.class,
            () -> inventory.reserve("O", "00001", "PROD-1", "WH", BigDecimal.ONE));
        assertThrows(IllegalStateException.class, () -> inventory.release("RES-1"));

        // non-2xx from the live stub: 409 reserve → InventoryUnavailable,
        // failed receive → IllegalStateException
        var live = new HttpIntegrations.HttpInventoryGateway("http://localhost:" + port);
        assertThrows(InventoryUnavailableException.class,
            () -> live.reserve("O", "00001", "PROD-OUT", "WH", BigDecimal.ONE));
        assertThrows(IllegalStateException.class,
            () -> live.receive("INV-9", BigDecimal.ONE));
    }

    @Test
    void httpPartyValidatorFailClosed() {
        var validator = new HttpIntegrations.HttpPartyValidator("http://localhost:" + port);
        assertTrue(validator.isValid("CUST-1"));
        assertFalse(validator.isValid("GHOST"));
        assertFalse(validator.isValid("UNREACHABLE"),
                "a dead party-svc must fail closed, not open");
    }

    @Test
    void httpPriceProviderResolvesAndEmpties() {
        var prices = new HttpIntegrations.HttpPriceSnapshotProvider(
            "http://localhost:" + port);
        var resolved = prices.resolve("PROD-1", "USD");
        assertTrue(resolved.isPresent());
        assertEquals(0, new BigDecimal("9.99").compareTo(resolved.get().price()));
        assertTrue(prices.resolve("PROD-X", "USD").isEmpty());
        assertTrue(prices.resolve("PROD-1", "EUR").isEmpty());
    }

    @Test
    void httpInventoryGatewayRoundTrips() {
        var inventory = new HttpIntegrations.HttpInventoryGateway(
            "http://localhost:" + port);
        var receipt = inventory.reserve("ORDER-H1", "00001", "PROD-1", "WH", BigDecimal.ONE);
        assertEquals("RES-1", receipt.reservationId());
        assertEquals("INV-1", receipt.inventoryItemId());
        inventory.release("RES-1");
        inventory.receive("INV-1", BigDecimal.ONE);
    }
}
