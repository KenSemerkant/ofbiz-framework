package billing.adapters.rest.persistence;

import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import billing.application.PaymentStore;
import billing.domain.Payment;
import billing.domain.PaymentStatus;

/** JPA adapter for the PaymentStore port. */
@Repository
public class JpaPaymentStore implements PaymentStore {

    private final PaymentRepository payments;

    public JpaPaymentStore(PaymentRepository payments) {
        this.payments = payments;
    }

    @Override
    public Optional<Payment> find(String paymentId) {
        return payments.findById(paymentId).map(this::toDomain);
    }

    @Override
    public Optional<Payment> findByIdempotencyKey(String idempotencyKey) {
        return payments.findByIdempotencyKey(idempotencyKey).map(this::toDomain);
    }

    @Override
    public List<Payment> capturedForInvoice(String invoiceId) {
        return payments.findByInvoiceIdAndStatus(invoiceId, PaymentStatus.CAPTURED.name()).stream()
            .map(this::toDomain).toList();
    }

    @Override
    public List<Payment> all() {
        return payments.findAll().stream().map(this::toDomain).toList();
    }

    @Override
    public void save(Payment payment) {
        payments.save(new PaymentEntity(payment.paymentId(), payment.invoiceId(),
            payment.idempotencyKey(), payment.amount(), payment.currencyUomId(),
            payment.status().name(), payment.gatewayRef(), payment.resultCode(),
            payment.createdAt()));
    }

    private Payment toDomain(PaymentEntity entity) {
        return Payment.rehydrate(entity.getPaymentId(), entity.getInvoiceId(),
            entity.getIdempotencyKey(), entity.getAmount(), entity.getCurrencyUomId(),
            PaymentStatus.valueOf(entity.getStatus()), entity.getGatewayRef(),
            entity.getResultCode());
    }
}
