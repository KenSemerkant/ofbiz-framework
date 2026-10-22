package inventory.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/**
 * P3-F2-S2 reservation contract: reserve/confirm/cancel is a one-shot state
 * machine. Why it matters: a confirmed reservation must never confirm again
 * (double QOH decrement) nor cancel (ATP leak).
 */
class ReservationTest {

    private Reservation reservation() {
        return new Reservation("RES-1", "ORD-1", "00001", "WIDGET", "WH1", "II1",
            new BigDecimal("2"));
    }

    @Test
    void reservationCarriesOrderLineAndStockLink() {
        Reservation reservation = reservation();
        assertEquals("RES-1", reservation.reservationId());
        assertEquals("ORD-1", reservation.orderId());
        assertEquals("00001", reservation.orderItemSeqId());
        assertEquals("II1", reservation.inventoryItemId());
        assertEquals(0, reservation.quantity().compareTo(new BigDecimal("2")));
        assertEquals(Reservation.Status.CREATED, reservation.status());
    }

    @Test
    void confirmAndCancelAreOneShot() {
        Reservation reservation = reservation();
        reservation.confirm();
        assertEquals(Reservation.Status.CONFIRMED, reservation.status());
        assertThrows(InvalidTransitionException.class, reservation::confirm);
        assertThrows(InvalidTransitionException.class, reservation::cancel);

        Reservation other = reservation();
        other.cancel();
        assertEquals(Reservation.Status.RELEASED, other.status());
        assertThrows(InvalidTransitionException.class, other::cancel);
        assertThrows(InvalidTransitionException.class, other::confirm);
    }

    @Test
    void guards() {        assertThrows(IllegalArgumentException.class,
            () -> new Reservation(" ", "ORD", "00001", "P", "F", "II", BigDecimal.ONE));
        assertThrows(IllegalArgumentException.class,
            () -> new Reservation("RES", null, "00001", "P", "F", "II", BigDecimal.ONE));
        assertThrows(IllegalArgumentException.class,
            () -> new Reservation("RES", "ORD", " ", "P", "F", "II", BigDecimal.ONE));
        assertThrows(IllegalArgumentException.class,
            () -> new Reservation("RES", "ORD", "00001", " ", "F", "II", BigDecimal.ONE));
        assertThrows(IllegalArgumentException.class,
            () -> new Reservation("RES", "ORD", "00001", "P", " ", "II", BigDecimal.ONE));
        assertThrows(IllegalArgumentException.class,
            () -> new Reservation("RES", "ORD", "00001", "P", "F", null, BigDecimal.ONE));
        assertThrows(IllegalArgumentException.class,
            () -> new Reservation("RES", "ORD", "00001", "P", "F", "II", BigDecimal.ZERO));
        assertThrows(IllegalArgumentException.class,
            () -> new Reservation("RES", "ORD", "00001", "P", "F", "II", null));
    }

    @Test
    void exceptionTypesCarryTheirMessages() {
        assertEquals("insufficient", new InsufficientStockException("insufficient").getMessage());
        assertEquals("invalid", new InvalidTransitionException("invalid").getMessage());
    }

    @Test
    void rehydrateRestoresPersistedValues() {
        Reservation reservation = Reservation.rehydrate("RES-9", "ORD-9", "00002", "GIZMO",
            "WH2", "II9", new BigDecimal("3"), Reservation.Status.CONFIRMED);
        assertEquals("GIZMO", reservation.productId());
        assertEquals("WH2", reservation.facilityId());
        assertEquals(Reservation.Status.CONFIRMED, reservation.status());
        assertThrows(InvalidTransitionException.class, reservation::confirm);
    }
}
