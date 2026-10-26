package order.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import order.domain.Order;
import order.domain.OrderLine;
import order.domain.OrderStatus;
import order.domain.OrderStatusChange;
import order.domain.Quote;
import order.domain.ReturnLine;
import order.domain.ReturnOrder;

/** Unit coverage for the remaining application services (module gate 95%). */
class ServiceSuiteTest {

    private static final OrderSerializer DUMMY = new OrderSerializer() {
        public String serializePlaced(Order o) { return "{}"; }
        public String serializeStatusChange(Order o, String n) { return "{}"; }
        public String serializeOrder(Order o) { return "{}"; }
        public String serializeLineNode(List<OrderLine> l) { return "[]"; }
        public String serializeReturn(order.domain.ReturnOrder r) { return "{}"; }
        public String serializeInvoiceRequest(Order o) { return "{}"; }
    };

    private static Order completedOrder(String orderId) {
        return Order.rehydrate(orderId, "CUST-1", null,
            List.of(new OrderLine("00001", "PROD-1", "WH", BigDecimal.ONE,
                new BigDecimal("9.99"), "USD")),
            OrderStatus.COMPLETED,
            List.of(new OrderStatusChange(OrderStatus.CREATED, Instant.now(), "placed"),
                new OrderStatusChange(OrderStatus.APPROVED, Instant.now(), "approved"),
                new OrderStatusChange(OrderStatus.COMPLETED, Instant.now(), "completed")));
    }

    private static Order createdOrder(String orderId) {
        return Order.rehydrate(orderId, "CUST-1", "PROMO",
            List.of(new OrderLine("00001", "PROD-1", "WH", BigDecimal.ONE,
                new BigDecimal("9.99"), "USD")),
            OrderStatus.CREATED,
            List.of(new OrderStatusChange(OrderStatus.CREATED, Instant.now(), "placed")));
    }

    @Test
    void lifecycleTransitionsEmitTheirEvents() {
        OrderEcaServiceTest.FakeOrderStore orders = new OrderEcaServiceTest.FakeOrderStore();
        List<String> events = new ArrayList<>();
        OrderLifecycleService lifecycle = new OrderLifecycleService(orders,
            (type, key, payload) -> events.add(type), DUMMY);
        orders.save(createdOrder("ORDER-L1"));

        assertTrue(lifecycle.get("ORDER-L1").isPresent());
        assertTrue(lifecycle.get("nope").isEmpty());
        assertEquals(OrderStatus.APPROVED, lifecycle.approve("ORDER-L1").status());
        assertEquals(OrderStatus.COMPLETED, lifecycle.complete("ORDER-L1").status());
        assertEquals(List.of("order.order.approved.v1", "order.order.completed.v1"), events);
        assertThrows(java.util.NoSuchElementException.class,
            () -> lifecycle.approve("missing"));

        orders.save(createdOrder("ORDER-L2"));
        assertEquals(OrderStatus.CANCELLED,
            lifecycle.cancel("ORDER-L2", "buyer remorse").status());
        assertTrue(events.contains("order.order.cancelled.v1"));
    }

    /** P9-F3: listings for the SPA order area (null filter = match all). */
    @Test
    void listingsReturnSavedAggregates() {
        OrderEcaServiceTest.FakeOrderStore orders = new OrderEcaServiceTest.FakeOrderStore();
        OrderLifecycleService lifecycle = new OrderLifecycleService(orders,
            (type, key, payload) -> { }, DUMMY);
        orders.save(createdOrder("ORDER-S1"));
        orders.save(createdOrder("ORDER-S2"));
        assertEquals(2, lifecycle.search(null, null).size());
        assertEquals(1, lifecycle.search(null, "CREATED").stream()
            .filter(o -> "ORDER-S1".equals(o.orderId())).count());
        assertEquals(0, lifecycle.search("NOBODY", null).size());

        Map<String, Quote> savedQuotes = new HashMap<>();
        QuoteService quotes = new QuoteService(new QuoteStore() {
            public Optional<Quote> find(String id) { return Optional.ofNullable(savedQuotes.get(id)); }
            public java.util.List<Quote> all() { return List.copyOf(savedQuotes.values()); }
            public void save(Quote q) { savedQuotes.put(q.quoteId(), q); }
        }, null);
        assertTrue(quotes.all().isEmpty());
    }

    @Test
    void quoteCreateAndConvertPreserveSnapshot() {
        Map<String, Quote> saved = new HashMap<>();
        class Store implements QuoteStore {
            public Optional<Quote> find(String id) { return Optional.ofNullable(saved.get(id)); }
            public java.util.List<Quote> all() { return List.copyOf(saved.values()); }
            public void save(Quote q) { saved.put(q.quoteId(), q); }
        }
        PlaceOrderSaga saga = new PlaceOrderSaga(new OrderEcaServiceTest.FakeOrderStore(),
            new OrderEcaServiceTest.InMemorySagaStore(),
            (type, key, payload) -> { }, DUMMY,
            partyId -> !"GHOST".equals(partyId),
            (productId, currency) -> Optional.of(
                new PriceSnapshotProvider.ResolvedPrice(new BigDecimal("5.00"), "USD")));
        QuoteService quotes = new QuoteService(new Store(), saga);

        Quote quote = quotes.create("CUST-1", "USD",
            List.of(new QuoteService.QuoteLine("PROD-1", "WH", BigDecimal.ONE)));
        assertEquals(0, new BigDecimal("5.00")
            .compareTo(quote.lines().get(0).unitPrice()));
        assertTrue(quotes.get(quote.quoteId()).isPresent());

        Order order = quotes.convert(quote.quoteId());
        assertEquals(0, new BigDecimal("5.00")
            .compareTo(order.lines().get(0).unitPrice()),
            "conversion must not re-price");
        assertTrue(saga.partyValidator().isValid("CUST-1"));
        assertTrue(saga.prices().resolve("PROD-1", "USD").isPresent());
        assertThrows(java.util.NoSuchElementException.class,
            () -> quotes.convert("QUOTE-GONE"));
        assertThrows(IllegalStateException.class, () -> quotes.convert(quote.quoteId()));
        assertThrows(PartyValidationException.class,
            () -> quotes.create("GHOST", "USD",
                List.of(new QuoteService.QuoteLine("PROD-1", "WH", BigDecimal.ONE))));
    }

    @Test
    void returnAdjustsInventoryAndEmitsCreditMemo() {
        OrderEcaServiceTest.FakeOrderStore orders = new OrderEcaServiceTest.FakeOrderStore();
        OrderEcaServiceTest.InMemorySagaStore sagas = new OrderEcaServiceTest.InMemorySagaStore();
        OrderEcaServiceTest.RecordingGateway inventory = new OrderEcaServiceTest.RecordingGateway();
        List<String> events = new ArrayList<>();
        class ReturnRows implements ReturnStore {
            final Map<String, ReturnOrder> rows = new HashMap<>();
            public Optional<ReturnOrder> find(String id) {
                return Optional.ofNullable(rows.get(id));
            }
            public java.util.List<ReturnOrder> all() { return List.copyOf(rows.values()); }
            public void save(ReturnOrder r) { rows.put(r.returnId(), r); }
        }
        ReturnService returns = new ReturnService(new ReturnRows(), orders, sagas,
            inventory, (type, key, payload) -> events.add(type), DUMMY);

        orders.save(completedOrder("ORDER-R1"));
        sagas.create("ORDER-R1");
        var receipt = new InventoryGateway.ReservationReceipt("RES-1", "INV-1", "PROD-1", "WH");
        sagas.markReserving("ORDER-R1");
        sagas.storeReservations("ORDER-R1", List.of(receipt));

        ReturnOrder created = returns.create("ORDER-R1",
            List.of(new ReturnLine("PROD-1", "WH", BigDecimal.ONE)));
        assertEquals(ReturnOrder.Status.COMPLETED, created.status());
        assertTrue(returns.get(created.returnId()).isPresent());
        assertEquals(List.of("INV-1"), inventory.received, "stock goes back on the shelf");
        assertTrue(events.contains("order.return.created.v1"));
        assertTrue(events.contains("order.creditmemo.issued.v1"));

        orders.save(createdOrder("ORDER-R2"));
        assertThrows(IllegalStateException.class,
            () -> returns.create("ORDER-R2", List.of(new ReturnLine("PROD-1", "WH",
                BigDecimal.ONE))), "returns need a COMPLETED order");
        assertThrows(java.util.NoSuchElementException.class,
            () -> returns.create("missing", List.of(new ReturnLine("PROD-1", "WH",
                BigDecimal.ONE))));
        orders.save(completedOrder("ORDER-R3"));
        sagas.create("ORDER-R3");
        assertThrows(IllegalStateException.class,
            () -> returns.create("ORDER-R3", List.of(new ReturnLine("PROD-X", "WH",
                BigDecimal.ONE))), "no receipt → cannot adjust inventory");
    }

    @Test
    void attributionRecordsOnlyCodedOrders() {
        List<AttributionStore.Attribution> rows = new ArrayList<>();
        class Store implements AttributionStore {
            public void record(Attribution a) { rows.add(a); }
            public long countByTrackingCode(String code) {
                return rows.stream().filter(r -> r.trackingCode().equals(code)).count();
            }
        }
        AttributionService attribution = new AttributionService(new Store());
        attribution.onOrderPlaced("ORDER-A1", "CUST-1", "PROMO-42", Instant.now());
        attribution.onOrderPlaced("ORDER-A2", "CUST-1", null, null);
        attribution.onOrderPlaced("ORDER-A3", "CUST-1", "  ", Instant.now());
        assertEquals(1, attribution.countByTrackingCode("PROMO-42"));
    }
}
