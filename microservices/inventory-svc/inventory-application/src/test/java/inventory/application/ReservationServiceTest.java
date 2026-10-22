package inventory.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
import inventory.domain.InsufficientStockException;
import inventory.domain.InventoryItem;
import inventory.domain.InvalidTransitionException;
import inventory.domain.Reservation;

/**
 * AC (P3-F2-S2): reserve draws ATP only, confirm converts (QOH down), cancel
 * compensates (ATP back) — each with its event in the same logical tx.
 */
class ReservationServiceTest {

    private static final BigDecimal TEN = new BigDecimal("10");

    private final Map<String, InventoryItem> itemData = new HashMap<>();
    private final Map<String, List<InventoryItem>> byProductFacility = new HashMap<>();
    private final Map<String, Reservation> reservationData = new HashMap<>();
    private final List<String> outboxEvents = new ArrayList<>();

    private final ReservationService service = new ReservationService(
        new InMemoryItemStore(), new InMemoryReservationStore(),
        (type, key, payload) -> outboxEvents.add(type), new StubSerializer());

    private void stocked(String itemId, String qty) {
        InventoryItem item = new InventoryItem(itemId, "WIDGET", "WH1", null, null);
        item.receive(new BigDecimal(qty));
        itemData.put(itemId, item);
        byProductFacility.computeIfAbsent("WIDGET|WH1", k -> new ArrayList<>()).add(item);
    }

    @Test
    void reserveDrawsAtpOnlyAndEmitsReserved() {
        stocked("II1", "10");

        Reservation reservation = service.reserve("ORD-1", "00001", "WIDGET", "WH1",
            new BigDecimal("4"));
        assertNotNull(reservation.reservationId());
        assertEquals(Reservation.Status.CREATED, reservation.status());
        assertEquals("II1", reservation.inventoryItemId());
        assertEquals(0, itemData.get("II1").quantityOnHandTotal().compareTo(TEN));
        assertEquals(0, itemData.get("II1").availableToPromiseTotal()
            .compareTo(new BigDecimal("6")));
        assertEquals(ReservationService.STOCK_RESERVED,
            outboxEvents.get(outboxEvents.size() - 1));
    }

    @Test
    void reservePicksTheCandidateWithSufficientAtp() {
        stocked("II1", "1");
        stocked("II2", "5");
        Reservation reservation = service.reserve("ORD-1", "00001", "WIDGET", "WH1",
            new BigDecimal("3"));
        assertEquals("II2", reservation.inventoryItemId());
        assertEquals(0, itemData.get("II1").availableToPromiseTotal()
            .compareTo(BigDecimal.ONE));
    }

    @Test
    void reserveFailures() {
        stocked("II1", "2");
        assertThrows(InsufficientStockException.class,
            () -> service.reserve("ORD-1", "00001", "WIDGET", "WH1", new BigDecimal("3")));
        assertThrows(NoSuchElementException.class,
            () -> service.reserve("ORD-1", "00001", "GIZMO", "WH1", BigDecimal.ONE));
        assertThrows(IllegalArgumentException.class,
            () -> service.reserve("ORD-1", "00001", "WIDGET", "WH1", BigDecimal.ZERO));
        assertTrue(reservationData.isEmpty(), "failed reserves must leave no rows");
    }

    @Test
    void confirmConvertsReservationAndDecrementsQoh() {
        stocked("II1", "10");
        Reservation reservation = service.reserve("ORD-1", "00001", "WIDGET", "WH1",
            new BigDecimal("4"));

        Reservation confirmed = service.confirm(reservation.reservationId());
        assertEquals(Reservation.Status.CONFIRMED, confirmed.status());
        assertEquals(0, itemData.get("II1").quantityOnHandTotal()
            .compareTo(new BigDecimal("6")));
        assertEquals(0, itemData.get("II1").availableToPromiseTotal()
            .compareTo(new BigDecimal("6")));
        assertEquals(ReservationService.STOCK_CONFIRMED,
            outboxEvents.get(outboxEvents.size() - 1));
        assertThrows(InvalidTransitionException.class,
            () -> service.confirm(reservation.reservationId()));
    }

    @Test
    void cancelReleasesAtpBack() {
        stocked("II1", "10");
        Reservation reservation = service.reserve("ORD-1", "00001", "WIDGET", "WH1",
            new BigDecimal("4"));

        Reservation released = service.cancel(reservation.reservationId());
        assertEquals(Reservation.Status.RELEASED, released.status());
        assertEquals(0, itemData.get("II1").quantityOnHandTotal().compareTo(TEN));
        assertEquals(0, itemData.get("II1").availableToPromiseTotal().compareTo(TEN));
        assertEquals(ReservationService.STOCK_RELEASED,
            outboxEvents.get(outboxEvents.size() - 1));
        assertThrows(InvalidTransitionException.class,
            () -> service.cancel(reservation.reservationId()));
    }

    @Test
    void unknownReservationIsNoSuchElement() {
        assertThrows(NoSuchElementException.class, () -> service.confirm("nope"));
        assertThrows(NoSuchElementException.class, () -> service.cancel("nope"));
        assertTrue(service.get("nope").isEmpty());
    }

    private class InMemoryItemStore implements InventoryItemStore {

        @Override
        public Optional<InventoryItem> find(String inventoryItemId) {
            return Optional.ofNullable(itemData.get(inventoryItemId));
        }

        @Override
        public List<InventoryItem> findByProductIdAndFacilityId(String productId,
                String facilityId) {
            return byProductFacility.getOrDefault(productId + "|" + facilityId, List.of());
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
        public List<Reservation> findByOrderIdAndStatus(String orderId,
                Reservation.Status status) {
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
        public String serializeFacility(inventory.domain.Facility facility) {
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
        public String serializeShipment(inventory.domain.Shipment shipment) {
            return "{}";
        }

        @Override
        public String serializeReservation(Reservation reservation, InventoryItem item) {
            return "{}";
        }
    }
}
