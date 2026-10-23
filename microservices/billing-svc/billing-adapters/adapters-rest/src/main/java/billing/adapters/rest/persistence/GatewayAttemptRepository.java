package billing.adapters.rest.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GatewayAttemptRepository
        extends JpaRepository<GatewayAttemptEntity, Long> {

    long countByPaymentId(String paymentId);

    List<GatewayAttemptEntity> findByPaymentId(String paymentId);
}
