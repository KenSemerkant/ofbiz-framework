package inventory.application;

import java.util.List;
import java.util.Optional;
import inventory.domain.Reservation;

/** Persistence port for reservations. */
public interface ReservationStore {

    Optional<Reservation> find(String reservationId);

    /** Live reservations of an order (P3-F2-S3 ship-order flow). */
    List<Reservation> findByOrderIdAndStatus(String orderId, Reservation.Status status);

    void save(Reservation reservation);
}
