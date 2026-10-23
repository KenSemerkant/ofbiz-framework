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
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import order.domain.Order;
import order.domain.OrderLine;
import order.domain.OrderStatus;
import order.domain.OrderStatusChange;

/**
 * P4-F2-S1 AC: compensation on partial failure, saga state persisted and
 * resumable after restart, ECA steps idempotent under redelivery.
 */
class OrderEcaServiceTest {

    /** In-memory saga store honouring the guarded-marker contract. */
    static class InMemorySagaStore implements SagaStore {

        record Row(Step step, List<InventoryGateway.ReservationReceipt> reservations) {
        }

        final Map<String, Row> rows = new HashMap<>();

        @Override
        public void create(String orderId) {
            rows.put(orderId, new Row(Step.PLACED, List.of()));
        }

        @Override
        public Optional<SagaState> find(String orderId) {
            Row row = rows.get(orderId);
            return row == null ? Optional.empty()
                : Optional.of(new SagaState(orderId, row.step(), row.reservations()));
        }

        @Override
        public boolean markReserving(String orderId) {
            Row row = rows.get(orderId);
            if (row == null || row.step() != Step.PLACED) {
                return false;
            }
            rows.put(orderId, new Row(Step.RESERVING, row.reservations()));
            return true;
        }

        @Override
        public void storeReservations(String orderId,
                List<InventoryGateway.ReservationReceipt> receipts) {
            rows.put(orderId, new Row(Step.RESERVED, receipts));
        }

        @Override
        public boolean markInvoiced(String orderId) {
            Row row = rows.get(orderId);
            if (row == null || row.step() != Step.RESERVED) {
                return false;
            }
            rows.put(orderId, new Row(Step.INVOICED, row.reservations()));
            return true;
        }

        @Override
        public void markDone(String orderId) {
            rows.put(orderId, new Row(Step.DONE, rows.get(orderId).reservations()));
        }

        @Override
        public void markCompensated(String orderId) {
            rows.put(orderId, new Row(Step.COMPENSATED, List.of()));
        }

        @Override
        public List<String> findIncompleteOrderIds() {
            return rows.entrySet().stream()
                .filter(e -> e.getValue().step() != Step.DONE
                    && e.getValue().step() != Step.COMPENSATED)
                .map(Map.Entry::getKey).toList();
        }
    }

    static class RecordingGateway implements InventoryGateway {

        final List<String> reserved = new ArrayList<>();
        final List<String> released = new ArrayList<>();
        final List<String> received = new ArrayList<>();
        final AtomicInteger reservationSeq = new AtomicInteger();
        boolean insufficient;

        @Override
        public ReservationReceipt reserve(String orderId, String orderItemSeqId,
                String productId, String facilityId, BigDecimal quantity) {
            if (insufficient && "PROD-2".equals(productId)) {
                throw new InventoryUnavailableException("no ATP for " + productId);
            }
            String reservationId = "RES-" + reservationSeq.incrementAndGet();
            reserved.add(productId);
            return new ReservationReceipt(reservationId, "INV-" + reservationId, productId,
                facilityId);
        }

        @Override
        public void release(String reservationId) {
            released.add(reservationId);
        }

        @Override
        public void receive(String inventoryItemId, BigDecimal quantity) {
            received.add(inventoryItemId);
        }
    }

    static class FakeOrderStore implements OrderStore {

        final Map<String, Order> orders = new HashMap<>();

        @Override
        public Optional<Order> find(String orderId) {
            return Optional.ofNullable(orders.get(orderId));
        }

        @Override
        public void save(Order order) {
            orders.put(order.orderId(), order);
        }
    }

    static class RecordingOutbox implements OutboxWriter {

        final List<String> events = new ArrayList<>();

        @Override
        public void append(String eventType, String aggregateKey, String payloadJson) {
            events.add(eventType);
        }
    }

    static final OrderSerializer DUMMY_SERIALIZER = new OrderSerializer() {
        @Override
        public String serializePlaced(Order order) {
            return "{}";
        }

        @Override
        public String serializeStatusChange(Order order, String note) {
            return "{}";
        }

        @Override
        public String serializeOrder(Order order) {
            return "{}";
        }

        @Override
        public String serializeLineNode(List<OrderLine> lines) {
            return "[]";
        }

        @Override
        public String serializeReturn(order.domain.ReturnOrder returns) {
            return "{}";
        }

        @Override
        public String serializeInvoiceRequest(Order order) {
            return "{}";
        }
    };

    private static Order placedOrder(String orderId) {
        return Order.rehydrate(orderId, "CUST-1", null,
            List.of(new OrderLine("00001", "PROD-1", "WH", BigDecimal.ONE,
                new BigDecimal("9.99"), "USD")),
            OrderStatus.CREATED,
            List.of(new OrderStatusChange(OrderStatus.CREATED, Instant.now(), "placed")));
    }

    @Test
    void placedOrderReservesStockAndTriggersInvoice() {
        FakeOrderStore orders = new FakeOrderStore();
        InMemorySagaStore sagas = new InMemorySagaStore();
        RecordingGateway inventory = new RecordingGateway();
        RecordingOutbox outbox = new RecordingOutbox();
        OrderEcaService eca = new OrderEcaService(orders, sagas, inventory, outbox,
            DUMMY_SERIALIZER);
        orders.save(placedOrder("ORDER-1"));
        sagas.create("ORDER-1");

        eca.onPlaced("ORDER-1");

        assertEquals(List.of("PROD-1"), inventory.reserved);
        assertEquals(List.of(OrderEcaService.INVOICE_REQUESTED), outbox.events);
        assertEquals(SagaStore.Step.DONE, sagas.find("ORDER-1").orElseThrow().step());
    }

    @Test
    void partialFailureReleasesPriorReservationsAndCompensates() {
        FakeOrderStore orders = new FakeOrderStore();
        InMemorySagaStore sagas = new InMemorySagaStore();
        RecordingGateway inventory = new RecordingGateway();
        OrderEcaService eca = new OrderEcaService(orders, sagas, inventory,
            new RecordingOutbox(), DUMMY_SERIALIZER);
        Order twoLines = Order.rehydrate("ORDER-2", "CUST-1", null,
            List.of(new OrderLine("00001", "PROD-1", "WH", BigDecimal.ONE,
                    new BigDecimal("9.99"), "USD"),
                new OrderLine("00002", "PROD-2", "WH", BigDecimal.ONE,
                    new BigDecimal("4.50"), "USD")),
            OrderStatus.CREATED,
            List.of(new OrderStatusChange(OrderStatus.CREATED, Instant.now(), "placed")));
        orders.save(twoLines);
        sagas.create("ORDER-2");
        inventory.insufficient = true; // second line will fail after the first reserved

        assertThrows(InventoryUnavailableException.class, () -> eca.onPlaced("ORDER-2"));
        assertTrue(inventory.released.contains("RES-1"), "first reservation must be released");
        assertEquals(SagaStore.Step.COMPENSATED,
            sagas.find("ORDER-2").orElseThrow().step());
    }

    @Test
    void redeliveryIsANoopAndRestartResumesPendingSaga() {
        FakeOrderStore orders = new FakeOrderStore();
        InMemorySagaStore sagas = new InMemorySagaStore();
        RecordingGateway inventory = new RecordingGateway();
        RecordingOutbox outbox = new RecordingOutbox();
        OrderEcaService eca = new OrderEcaService(orders, sagas, inventory, outbox,
            DUMMY_SERIALIZER);
        orders.save(placedOrder("ORDER-3"));
        sagas.create("ORDER-3");

        eca.onPlaced("ORDER-3");
        eca.onPlaced("ORDER-3"); // Kafka redelivery
        assertEquals(1, inventory.reserved.size(), "redelivery must not re-reserve");
        assertEquals(1, outbox.events.size(), "redelivery must not re-invoice");

        // restart scenario: a saga stuck at PLACED (order placed, service
        // crashed before the ECA consumer ran) — a NEW service instance
        // picks it up via resumePending
        orders.save(placedOrder("ORDER-4"));
        sagas.create("ORDER-4");
        OrderEcaService restarted = new OrderEcaService(orders, sagas, inventory, outbox,
            DUMMY_SERIALIZER);
        restarted.resumePending();
        assertEquals(SagaStore.Step.DONE, sagas.find("ORDER-4").orElseThrow().step());
        assertEquals(2, inventory.reserved.size(), "the pending saga must be re-driven");

        // resume with stock gone (PROD-2 is the product the stub starves):
        // the pending saga is compensated, not dropped
        orders.save(Order.rehydrate("ORDER-5", "CUST-1", null,
            List.of(new OrderLine("00001", "PROD-2", "WH", BigDecimal.ONE,
                new BigDecimal("4.50"), "USD")),
            OrderStatus.CREATED,
            List.of(new order.domain.OrderStatusChange(OrderStatus.CREATED, Instant.now(),
                "placed"))));
        sagas.create("ORDER-5");
        inventory.insufficient = true;
        restarted.resumePending();
        assertEquals(SagaStore.Step.COMPENSATED, sagas.find("ORDER-5").orElseThrow().step());

        // explicit compensation releases the recorded reservations
        orders.save(placedOrder("ORDER-6"));
        sagas.create("ORDER-6");
        sagas.markReserving("ORDER-6");
        var receipt = new InventoryGateway.ReservationReceipt("RES-9", "INV-9", "PROD-1", "WH");
        sagas.storeReservations("ORDER-6", List.of(receipt));
        restarted.compensate("ORDER-6");
        assertTrue(inventory.released.contains("RES-9"));
        restarted.compensate("missing-saga"); // unknown saga: no-op, no throw
    }
}
