package inventory.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import inventory.application.DuplicateException;
import inventory.domain.Facility;
import inventory.domain.InventoryItem;
import inventory.domain.InvalidTransitionException;
import inventory.domain.Reservation;
import inventory.domain.Shipment;

/**
 * AC (P3-F2-S3): ship-order flow — create→pack→ship transitions, ship()
 * confirms the linked order's live reservations (QOH leaves), and every
 * transition emits its event in the same logical transaction.
 */
class ShipmentServiceTest {

    private static final BigDecimal TEN = new BigDecimal("10");

    private final Map<String, Shipment> shipmentData = new HashMap<>();
    private final Map<String, InventoryItem> itemData = new HashMap<>();
    private final Map<String, Reservation> reservationData = new HashMap<>();
    private final List<String> outboxEvents = new ArrayList<>();

    private final ShipmentService service = new ShipmentService(
        new InMemoryShipmentStore(),
        new ReservationService(new InMemoryItemStore(), new InMemoryReservationStore(),
            (type, key, payload) -> outboxEvents.add(type), new StubSerializer()),
        (type, key, payload) -> outboxEvents.add(type), new StubSerializer());

    private void stocked() {
        InventoryItem item = new InventoryItem("II1", "WIDGET", "WH1", null, null);
        item.receive(TEN);
        itemData.put("II1", item);
    }

    @Test
    void createPackShipEmitsUpdatedPerTransition() {
        Shipment shipment = new Shipment("SH-1", "ORD-1");
        shipment.addShipmentItem("WIDGET", new BigDecimal("2"), "II1");
        service.create(shipment);
        assertEquals(List.of(ShipmentService.SHIPMENT_UPDATED), outboxEvents);

        service.pack("SH-1");
        assertEquals(Shipment.Status.PACKED, service.get("SH-1").orElseThrow().status());
        assertEquals(2, outboxEvents.stream()
            .filter(ShipmentService.SHIPMENT_UPDATED::equals).count());

        assertThrows(DuplicateException.class,
            () -> service.create(new Shipment("SH-1", null)));
    }

    @Test
    void shipConfirmsOrderReservationsAndDecrementsQoh() {
        stocked();
        ReservationService reservations = new ReservationService(new InMemoryItemStore(),
            new InMemoryReservationStore(),
            (type, key, payload) -> outboxEvents.add(type), new StubSerializer());
        reservations.reserve("ORD-1", "00001", "WIDGET", "WH1", new BigDecimal("4"));
        reservations.reserve("ORD-1", "00002", "WIDGET", "WH1", new BigDecimal("3"));

        Shipment shipment = new Shipment("SH-2", "ORD-1");
        shipment.addShipmentItem("WIDGET", new BigDecimal("7"), "II1");
        service.create(shipment);
        service.pack("SH-2");
        service.ship("SH-2");

        assertEquals(Shipment.Status.SHIPPED, service.get("SH-2").orElseThrow().status());
        // both reservations confirmed, QOH down by 7, ATP already down at reserve
        assertEquals(0, itemData.get("II1").quantityOnHandTotal()
            .compareTo(new BigDecimal("3")));
        assertEquals(0, itemData.get("II1").availableToPromiseTotal()
            .compareTo(new BigDecimal("3")));
        assertEquals(2, outboxEvents.stream()
            .filter(ReservationService.STOCK_CONFIRMED::equals).count());
        assertEquals(1, outboxEvents.stream()
            .filter(ShipmentService.STOCK_SHIPPED::equals).count());
    }

    @Test
    void shipWithoutOrderJustTransitions() {
        Shipment shipment = new Shipment("SH-3", null);
        service.create(shipment);
        service.pack("SH-3");
        service.ship("SH-3");
        assertEquals(1, outboxEvents.stream()
            .filter(ShipmentService.STOCK_SHIPPED::equals).count());
    }

    @Test
    void invalidTransitionsAndUnknownShipments() {
        Shipment shipment = new Shipment("SH-4", null);
        service.create(shipment);
        assertThrows(InvalidTransitionException.class, () -> service.ship("SH-4"));
        assertThrows(NoSuchElementException.class, () -> service.pack("nope"));
        assertThrows(NoSuchElementException.class, () -> service.ship("nope"));
        assertTrue(service.get("nope").isEmpty());
    }

    private class InMemoryShipmentStore implements ShipmentStore {

        @Override
        public Optional<Shipment> find(String shipmentId) {
            return Optional.ofNullable(shipmentData.get(shipmentId));
        }

        @Override
        public void save(Shipment shipment) {
            shipmentData.put(shipment.shipmentId(), shipment);
        }
    }

    private class InMemoryItemStore implements InventoryItemStore {

        @Override
        public Optional<InventoryItem> find(String inventoryItemId) {
            return Optional.ofNullable(itemData.get(inventoryItemId));
        }

        @Override
        public List<InventoryItem> findByProductIdAndFacilityId(String productId,
                String facilityId) {
            return itemData.values().stream()
                .filter(i -> i.productId().equals(productId)
                    && i.facilityId().equals(facilityId))
                .toList();
        }

        @Override
        public void save(InventoryItem item) {
            itemData.put(item.inventoryItemId(), item);
        }
    }

    private class InMemoryReservationStore implements ReservationStore {

        @Override
        public Optional<Reservation> find(String reservationId) {
            return Optional.ofNullable(reservationData.get(reservationId));
        }

        @Override
        public List<Reservation> findByOrderIdAndStatus(String orderId, Reservation.Status status) {
            return reservationData.values().stream()
                .filter(r -> r.orderId().equals(orderId) && r.status() == status)
                .toList();
        }

        @Override
        public void save(Reservation reservation) {
            reservationData.put(reservation.reservationId(), reservation);
        }
    }

    private static final class StubSerializer implements InventorySerializer {

        @Override
        public String serializeItem(InventoryItem item) {
            return "{}";
        }

        @Override
        public String serializeFacility(Facility facility) {
            return "{}";
        }

        @Override
        public String serializeFacilityDeleted(String facilityId) {
            return "{}";
        }

        @Override
        public String serializeStockChange(InventoryItem item, BigDecimal quantity) {
            return "{}";
        }

        @Override
        public String serializeTransfer(InventoryItem from, InventoryItem to,
                BigDecimal quantity) {
            return "{}";
        }

        @Override
        public String serializeReservation(Reservation reservation, InventoryItem item) {
            return "{}";
        }

        @Override
        public String serializeShipment(inventory.domain.Shipment shipment) {
            return "{}";
        }
    }
}
