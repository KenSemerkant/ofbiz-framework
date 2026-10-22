package inventory.adapters.rest.api;

import java.math.BigDecimal;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import inventory.application.ReservationService;
import inventory.domain.Reservation;

/**
 * Adapter-layer transaction boundary for the reservation saga (ADR-009):
 * each call is ONE transaction spanning the item mutation, the reservation
 * row and the outbox event. A separate bean from the controller so the
 * controller can retry the WHOLE transaction on optimistic-lock conflicts —
 * retrying inside a @Transactional method cannot work, the failed tx is
 * already rollback-only (P3-F2-S2).
 */
@Component
public class ReservationOperations {

    private final ReservationService reservations;

    public ReservationOperations(ReservationService reservations) {
        this.reservations = reservations;
    }

    @Transactional
    public Reservation reserve(String orderId, String orderItemSeqId, String productId,
            String facilityId, BigDecimal quantity) {
        return reservations.reserve(orderId, orderItemSeqId, productId, facilityId, quantity);
    }

    @Transactional
    public Reservation confirm(String reservationId) {
        return reservations.confirm(reservationId);
    }

    @Transactional
    public Reservation cancel(String reservationId) {
        return reservations.cancel(reservationId);
    }
}
