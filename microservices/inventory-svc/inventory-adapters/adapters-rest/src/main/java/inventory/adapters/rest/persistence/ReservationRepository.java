package inventory.adapters.rest.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReservationRepository extends JpaRepository<ReservationEntity, String> {

    /** Live reservations of an order (P3-F2-S3 ship-order flow). */
    List<ReservationEntity> findByOrderIdAndStatus(String orderId, String status);
}
