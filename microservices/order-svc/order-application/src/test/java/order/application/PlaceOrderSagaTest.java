package order.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import order.domain.Order;
import order.domain.OrderLine;
import order.domain.OrderStatus;

/** P4-F1-S2 / P4-F2-S1: party validation, price snapshot, placed event. */
class PlaceOrderSagaTest {

    static class StubOutbox implements OutboxWriter {

        final List<String> events = new ArrayList<>();

        @Override
        public void append(String eventType, String aggregateKey, String payloadJson) {
            events.add(eventType + ":" + aggregateKey);
        }
    }

    static final OrderSerializer DUMMY = new OrderSerializer() {
        public String serializePlaced(Order o) { return "{}"; }
        public String serializeStatusChange(Order o, String n) { return "{}"; }
        public String serializeOrder(Order o) { return "{}"; }
        public String serializeLineNode(List<OrderLine> l) { return "[]"; }
        public String serializeReturn(order.domain.ReturnOrder r) { return "{}"; }
        public String serializeInvoiceRequest(Order o) { return "{}"; }
    };

    @Test
    void placesOrderWithSnapshotPricesAndEmitsPlaced() {
        StubOutbox outbox = new StubOutbox();
        Map<String, Order> saved = new HashMap<>();
        OrderEcaServiceTest.InMemorySagaStore sagas =
            new OrderEcaServiceTest.InMemorySagaStore();
        PlaceOrderSaga saga = new PlaceOrderSaga(
            new OrderEcaServiceTest.FakeOrderStore() {
                public void save(Order order) {
                    saved.put(order.orderId(), order);
                }
            },
            sagas, outbox, DUMMY,
            partyId -> true,
            (productId, currency) -> Optional.of(
                new PriceSnapshotProvider.ResolvedPrice(new BigDecimal("9.99"), "USD")));

        Order order = saga.place("CUST-1", "USD",
            List.of(new PlaceOrderSaga.CartLine("PROD-1", "WH", new BigDecimal("2"))), null);

        assertEquals(OrderStatus.CREATED, saved.get(order.orderId()).status());
        assertEquals(new BigDecimal("9.99"), order.lines().get(0).unitPrice(),
            "the resolved price must be snapshotted on the line");
        assertEquals(1, outbox.events.size());
        assertEquals("order.order.placed.v1:" + order.orderId(), outbox.events.get(0));
    }

    @Test
    void unknownPartyWritesNothing() {
        StubOutbox outbox = new StubOutbox();
        PlaceOrderSaga saga = new PlaceOrderSaga(new OrderEcaServiceTest.FakeOrderStore(),
            new OrderEcaServiceTest.InMemorySagaStore(), outbox, DUMMY,
            partyId -> false,
            (productId, currency) -> Optional.of(
                new PriceSnapshotProvider.ResolvedPrice(BigDecimal.TEN, "USD")));

        assertThrows(PartyValidationException.class,
            () -> saga.place("GHOST", "USD",
                List.of(new PlaceOrderSaga.CartLine("PROD-1", "WH", BigDecimal.ONE)), null));
        assertEquals(0, outbox.events.size(), "no order state may exist for a rejected party");
    }

    @Test
    void unpricedLineRejectsTheWholeCart() {
        StubOutbox outbox = new StubOutbox();
        PlaceOrderSaga saga = new PlaceOrderSaga(new OrderEcaServiceTest.FakeOrderStore(),
            new OrderEcaServiceTest.InMemorySagaStore(), outbox, DUMMY,
            partyId -> true, (productId, currency) -> Optional.empty());

        assertThrows(PriceUnavailableException.class,
            () -> saga.place("CUST-1", "USD",
                List.of(new PlaceOrderSaga.CartLine("PROD-X", "WH", BigDecimal.ONE)), null));
        assertEquals(0, outbox.events.size());
    }
}
