package inventory.adapters.rest.persistence;

import java.util.Optional;
import org.springframework.stereotype.Repository;
import inventory.application.ReservationStore;
import inventory.domain.Reservation;

/** JPA adapter for the ReservationStore port. */
@Repository
public class JpaReservationStore implements ReservationStore {

    private final ReservationRepository reservations;

    public JpaReservationStore(ReservationRepository reservations) {
        this.reservations = reservations;
    }

    @Override
    public Optional<Reservation> find(String reservationId) {
        return reservations.findById(reservationId).map(ReservationEntity::toDomain);
    }

    @Override
    public void save(Reservation reservation) {
        ReservationEntity entity = reservations.findById(reservation.reservationId())
            .orElseGet(() -> new ReservationEntity(reservation.reservationId()));
        entity.applyFrom(reservation);
        reservations.save(entity);
    }
}
