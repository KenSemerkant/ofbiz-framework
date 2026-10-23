package billing.adapters.rest.persistence;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<PaymentEntity, String> {

    Optional<PaymentEntity> findByIdempotencyKey(String idempotencyKey);

    List<PaymentEntity> findByInvoiceIdAndStatus(String invoiceId, String status);
}
