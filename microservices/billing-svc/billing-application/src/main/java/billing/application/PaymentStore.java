package billing.application;

import java.util.List;
import java.util.Optional;
import billing.domain.Payment;

/** Persistence port for the Payment aggregate. */
public interface PaymentStore {

    Optional<Payment> find(String paymentId);

    /** Idempotency-key lookup (P5-F1-S2): a retried create must not double-charge. */
    Optional<Payment> findByIdempotencyKey(String idempotencyKey);

    List<Payment> capturedForInvoice(String invoiceId);

    List<Payment> all();

    void save(Payment payment);
}
