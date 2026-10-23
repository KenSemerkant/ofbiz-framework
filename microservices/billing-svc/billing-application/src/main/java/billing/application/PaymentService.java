package billing.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import billing.domain.GatewayAttempt;
import billing.domain.GatewayResult;
import billing.domain.Payment;

/**
 * P5-F1-S2 payment use case: auth/capture/void through the gateway adapter
 * port with idempotency keys, an append-only gateway-attempt log, and a
 * reconciliation view over stuck payments. Gateway outages propagate so the
 * Kafka error handler can DLQ them; nothing is dropped silently.
 */
public class PaymentService {

    public static final String PAYMENT_CAPTURED = "billing.payment.captured.v1";
    public static final String PAYMENT_FAILED = "billing.payment.failed.v1";

    /** Reconciliation row: what the gateway log says vs where the payment is. */
    public record ReconciliationRow(String paymentId, String invoiceId, String status,
            int attempts, String lastResultCode) {
    }

    private final PaymentStore payments;
    private final InvoiceStore invoices;
    private final GatewayAttemptStore gatewayAttempts;
    private final PaymentGateway gateway;
    private final OutboxWriter outbox;
    private final BillingSerializer serializer;
    private final InvoiceService invoiceService;

    public PaymentService(PaymentStore payments, InvoiceStore invoices,
            GatewayAttemptStore gatewayAttempts, PaymentGateway gateway, OutboxWriter outbox,
            BillingSerializer serializer, InvoiceService invoiceService) {
        this.payments = payments;
        this.invoices = invoices;
        this.gatewayAttempts = gatewayAttempts;
        this.gateway = gateway;
        this.outbox = outbox;
        this.serializer = serializer;
        this.invoiceService = invoiceService;
    }

    /**
     * Idempotent create: the same idempotency key always returns the
     * original payment — a client retry can never double-charge.
     */
    public Payment create(String paymentId, String invoiceId, String idempotencyKey,
            BigDecimal amount, String currencyUomId) {
        Optional<Payment> existing = payments.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            return existing.orElseThrow();
        }
        Payment payment = new Payment(paymentId, invoiceId, idempotencyKey, amount,
            currencyUomId, Instant.now());
        payments.save(payment);
        return payment;
    }

    public Optional<Payment> get(String paymentId) {
        return payments.find(paymentId);
    }

    public Payment authorize(String paymentId) {
        Payment payment = load(paymentId);
        GatewayResult result = call("authorize",
            () -> gateway.authorize(payment.idempotencyKey(), payment.amount(),
                payment.currencyUomId()), payment);
        if (result.approved()) {
            payment.authorize(result.refNum(), result.resultCode());
        } else {
            payment.fail(result.resultCode());
        }
        payments.save(payment);
        if (!result.approved()) {
            outbox.append(PAYMENT_FAILED, payment.paymentId(),
                serializer.serializePayment(payment));
        }
        return payment;
    }

    public Payment capture(String paymentId) {
        Payment payment = load(paymentId);
        GatewayResult result = call("capture",
            () -> gateway.capture(payment.idempotencyKey(), payment.gatewayRef(),
                payment.amount()), payment);
        if (result.approved()) {
            payment.capture(result.resultCode());
        } else {
            payment.fail(result.resultCode());
        }
        payments.save(payment);
        if (payment.status() == billing.domain.PaymentStatus.CAPTURED) {
            outbox.append(PAYMENT_CAPTURED, payment.paymentId(),
                serializer.serializePayment(payment));
            invoiceService.settleIfFullyApplied(
                invoices.find(payment.invoiceId()).orElseThrow());
        } else {
            outbox.append(PAYMENT_FAILED, payment.paymentId(),
                serializer.serializePayment(payment));
        }
        return payment;
    }

    public Payment voidAuthorization(String paymentId) {
        Payment payment = load(paymentId);
        GatewayResult result = call("void",
            () -> gateway.voidAuthorization(payment.idempotencyKey(), payment.gatewayRef()),
            payment);
        if (result.approved()) {
            payment.voidAuthorization(result.resultCode());
        } else {
            payment.fail(result.resultCode());
        }
        payments.save(payment);
        return payment;
    }

    @FunctionalInterface
    private interface GatewayCall {
        GatewayResult run();
    }

    /** Every adapter call is logged; outages propagate for the DLQ path. */
    private GatewayResult call(String operation, GatewayCall call, Payment payment) {
        GatewayResult result;
        try {
            result = call.run();
        } catch (GatewayUnavailableException e) {
            gatewayAttempts.append(new GatewayAttempt(payment.paymentId(), operation,
                payment.idempotencyKey(), false, null, "UNAVAILABLE", Instant.now()));
            throw e;
        }
        gatewayAttempts.append(new GatewayAttempt(payment.paymentId(), operation,
            payment.idempotencyKey(), result.approved(), result.refNum(), result.resultCode(),
            Instant.now()));
        return result;
    }

    /**
     * P5-F1-S2 reconciliation report: stuck (PENDING) and failed payments
     * with their gateway attempt count — what an operator works through
     * after a DLQ/outage event. AUTHORIZED awaiting capture is a normal
     * state and stays off the report.
     */
    public List<ReconciliationRow> reconciliationReport() {
        return payments.all().stream()
            .filter(p -> p.status() == billing.domain.PaymentStatus.PENDING
                || p.status() == billing.domain.PaymentStatus.FAILED)
            .map(p -> new ReconciliationRow(p.paymentId(), p.invoiceId(), p.status().name(),
                (int) gatewayAttempts.all().stream()
                    .filter(a -> a.paymentId().equals(p.paymentId())).count(),
                p.resultCode()))
            .toList();
    }

    private Payment load(String paymentId) {
        return payments.find(paymentId)
            .orElseThrow(() -> new NoSuchElementException("no payment " + paymentId));
    }
}
