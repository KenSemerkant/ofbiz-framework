package billing.adapters.rest.api;

import java.math.BigDecimal;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.function.Supplier;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import billing.application.InvoiceService;
import billing.application.PaymentService;
import billing.application.PdfRenderer;
import billing.domain.BillingAccount;
import billing.domain.CreditLimitExceededException;
import billing.domain.IllegalInvoiceTransitionException;
import billing.domain.IllegalPaymentTransitionException;
import billing.domain.Invoice;
import billing.domain.InvoiceLine;
import billing.domain.Payment;

/**
 * P5-F1 REST edge: /api/v1/invoices, /api/v1/payments,
 * /api/v1/billing-accounts. Guard violations map to typed 409s (the same
 * convention as order-svc); mutating endpoints run in one adapter-layer tx
 * so the aggregate + outbox row commit together.
 */
@RestController
@RequestMapping("/api/v1")
public class BillingController {

    private final InvoiceService invoices;
    private final PaymentService payments;
    private final PdfRenderer pdf;
    private final billing.application.BillingAccountStore accounts;

    public BillingController(InvoiceService invoices, PaymentService payments, PdfRenderer pdf,
            billing.application.BillingAccountStore accounts) {
        this.invoices = invoices;
        this.payments = payments;
        this.pdf = pdf;
        this.accounts = accounts;
    }

    public record LineRequest(String invoiceItemSeqId, String invoiceItemTypeId,
            String description, BigDecimal quantity, BigDecimal amount) {
    }

    public record CreateInvoiceRequest(String invoiceId, String customerId,
            String billingAccountId, String currencyUomId, List<LineRequest> lines) {
    }

    public record CreatePaymentRequest(String paymentId, String invoiceId,
            String idempotencyKey, BigDecimal amount, String currencyUomId) {
    }

    public record CreateAccountRequest(String billingAccountId, String customerId,
            BigDecimal creditLimit, Integer netDaysTerm) {
    }

    public record InvoiceResponse(String invoiceId, String orderId, String customerId,
            String billingAccountId, String status, BigDecimal total, String currencyUomId,
            List<InvoiceLine> lines) {
    }

    public record PaymentResponse(String paymentId, String invoiceId, String status,
            BigDecimal amount, String currencyUomId, String idempotencyKey, String gatewayRef,
            String resultCode) {
    }

    public record AccountResponse(String billingAccountId, String customerId,
            BigDecimal creditLimit, BigDecimal outstandingArBalance, Integer netDaysTerm) {
    }

    public record NoteRequest(String note) {
    }

    @PostMapping("/invoices")
    @Transactional
    ResponseEntity<InvoiceResponse> createInvoice(@RequestBody CreateInvoiceRequest request) {
        List<InvoiceLine> lines = request.lines().stream()
            .map(l -> new InvoiceLine(l.invoiceItemSeqId(), l.invoiceItemTypeId(),
                l.description(), l.quantity(), l.amount()))
            .toList();
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(toResponse(invoices.createDraft(request.invoiceId(), request.customerId(),
                request.currencyUomId(), request.billingAccountId(), lines)));
    }

    @GetMapping("/invoices/{invoiceId}")
    ResponseEntity<InvoiceResponse> getInvoice(@PathVariable String invoiceId) {
        return invoices.get(invoiceId).map(this::toResponse).map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/invoices/{invoiceId}/issue")
    @Transactional
    ResponseEntity<InvoiceResponse> issue(@PathVariable String invoiceId) {
        return guarded(() -> toResponse(invoices.issue(invoiceId)));
    }

    @PostMapping("/invoices/{invoiceId}/cancel")
    @Transactional
    ResponseEntity<InvoiceResponse> cancel(@PathVariable String invoiceId,
            @RequestBody(required = false) NoteRequest note) {
        return guarded(() -> toResponse(invoices.cancel(invoiceId,
            note == null ? null : note.note())));
    }

    @PostMapping("/invoices/{invoiceId}/writeoff")
    @Transactional
    ResponseEntity<InvoiceResponse> writeOff(@PathVariable String invoiceId,
            @RequestBody(required = false) NoteRequest note) {
        return guarded(() -> toResponse(invoices.writeOff(invoiceId,
            note == null ? null : note.note())));
    }

    /** P5-F1-S1 AC: PDF render adapter serves the invoice document. */
    @GetMapping(value = "/invoices/{invoiceId}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @Transactional(readOnly = true)
    ResponseEntity<byte[]> invoicePdf(@PathVariable String invoiceId) {
        Invoice invoice = invoices.get(invoiceId).orElseThrow();
        return ResponseEntity.ok().header("Content-Disposition",
                "attachment; filename=invoice-" + invoiceId + ".pdf")
            .body(pdf.render(invoice));
    }

    @PostMapping("/payments")
    @Transactional
    ResponseEntity<PaymentResponse> createPayment(@RequestBody CreatePaymentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(toResponse(payments.create(request.paymentId(), request.invoiceId(),
                request.idempotencyKey(), request.amount(), request.currencyUomId())));
    }

    @GetMapping("/payments/{paymentId}")
    ResponseEntity<PaymentResponse> getPayment(@PathVariable String paymentId) {
        return payments.get(paymentId).map(this::toResponse).map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/payments/{paymentId}/authorize")
    @Transactional
    ResponseEntity<PaymentResponse> authorize(@PathVariable String paymentId) {
        return guarded(() -> toResponse(payments.authorize(paymentId)));
    }

    @PostMapping("/payments/{paymentId}/capture")
    @Transactional
    ResponseEntity<PaymentResponse> capture(@PathVariable String paymentId) {
        return guarded(() -> toResponse(payments.capture(paymentId)));
    }

    @PostMapping("/payments/{paymentId}/void")
    @Transactional
    ResponseEntity<PaymentResponse> voidAuthorization(@PathVariable String paymentId) {
        return guarded(() -> toResponse(payments.voidAuthorization(paymentId)));
    }

    /** P5-F1-S2 AC: reconciliation report over stuck/failed payments. */
    @GetMapping("/payments/reconciliation")
    List<PaymentService.ReconciliationRow> reconciliation() {
        return payments.reconciliationReport();
    }

    @PostMapping("/billing-accounts")
    @Transactional
    ResponseEntity<AccountResponse> createAccount(@RequestBody CreateAccountRequest request) {
        BillingAccount account = new BillingAccount(request.billingAccountId(),
            request.customerId(), request.creditLimit(), request.netDaysTerm());
        accounts.save(account);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(account));
    }

    @GetMapping("/billing-accounts/{billingAccountId}")
    ResponseEntity<AccountResponse> getAccount(@PathVariable String billingAccountId) {
        return accounts.find(billingAccountId).map(this::toResponse)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private InvoiceResponse toResponse(Invoice invoice) {
        return new InvoiceResponse(invoice.invoiceId(), invoice.orderId(), invoice.customerId(),
            invoice.billingAccountId(), invoice.status().name(), invoice.total(),
            invoice.currencyUomId(), invoice.lines());
    }

    private PaymentResponse toResponse(Payment payment) {
        return new PaymentResponse(payment.paymentId(), payment.invoiceId(),
            payment.status().name(), payment.amount(), payment.currencyUomId(),
            payment.idempotencyKey(), payment.gatewayRef(), payment.resultCode());
    }

    private AccountResponse toResponse(BillingAccount account) {
        return new AccountResponse(account.billingAccountId(), account.customerId(),
            account.creditLimit(), account.outstandingArBalance(), account.netDaysTerm());
    }

    /** @return 409 with the typed error when a guard rejects the operation. */
    private <T> ResponseEntity<T> guarded(Supplier<T> action) {
        try {
            return ResponseEntity.ok(action.get());
        } catch (IllegalInvoiceTransitionException | IllegalPaymentTransitionException
                | CreditLimitExceededException | IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .header("X-Error-Reason", e.getClass().getSimpleName()).build();
        }
    }

    @ExceptionHandler(NoSuchElementException.class)
    ResponseEntity<String> notFound(Exception e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
    }
}
