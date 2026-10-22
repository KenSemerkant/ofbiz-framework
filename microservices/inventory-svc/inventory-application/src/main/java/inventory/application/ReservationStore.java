package inventory.application;

import java.util.Optional;
import inventory.domain.Reservation;

/** Persistence port for reservations. */
public interface ReservationStore {

    Optional<Reservation> find(String reservationId);

    void save(Reservation reservation);
}
