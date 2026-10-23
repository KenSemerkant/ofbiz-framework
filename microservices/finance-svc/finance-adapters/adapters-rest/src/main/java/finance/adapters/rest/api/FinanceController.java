package finance.adapters.rest.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.function.Supplier;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import finance.application.GlService;
import finance.application.ParkedPostingStore;
import finance.application.PostingRuleStore;
import finance.domain.AlreadyPostedException;
import finance.domain.ClosedPeriodException;
import finance.domain.DebitCredit;
import finance.domain.GlEntry;
import finance.domain.GlTransaction;
import finance.domain.Period;
import finance.domain.PostingRule;
import finance.domain.UnbalancedTransactionException;

/**
 * P5-F2 REST edge: /api/v1/gl — accounts, transactions, periods, posting
 * rules, parked postings, and the trial balance. Guard violations map to
 * typed 409s; mutating endpoints run in one adapter-layer tx so the
 * aggregate + outbox row commit together.
 */
@RestController
@RequestMapping("/api/v1/gl")
public class FinanceController {

    private final GlService gl;
    private final PostingRuleStore rules;
    private final ParkedPostingStore parked;

    public FinanceController(GlService gl, PostingRuleStore rules, ParkedPostingStore parked) {
        this.gl = gl;
        this.rules = rules;
        this.parked = parked;
    }

    public record CreateAccountRequest(String glAccountId, String accountCode,
            String accountName, String postBalance, String glAccountClassId) {
    }

    public record CreatePeriodRequest(String periodId, String fromDate, String thruDate) {
    }

    public record EntryRequest(String glAccountId, BigDecimal amount, String debitCredit) {
    }

    public record PostTransactionRequest(String acctgTransId, String transType,
            String description, String periodId, List<EntryRequest> entries) {
    }

    public record CreateRuleRequest(String ruleId, String eventType, String debitAccountId,
            String creditAccountId, boolean enabled) {
    }

    public record AccountResponse(String glAccountId, String accountCode, String accountName,
            String postBalance, String glAccountClassId) {
    }

    public record EntryResponse(String glAccountId, BigDecimal amount, String debitCredit) {
    }

    public record TransactionResponse(String acctgTransId, String transType,
            String description, String periodId, boolean posted, List<EntryResponse> entries) {
    }

    public record PeriodResponse(String periodId, String fromDate, String thruDate,
            boolean closed) {
    }

    public record RuleResponse(String ruleId, String eventType, String debitAccountId,
            String creditAccountId, boolean enabled) {
    }

    @PostMapping("/accounts")
    @Transactional
    ResponseEntity<AccountResponse> createAccount(@RequestBody CreateAccountRequest request) {
        var account = gl.createAccount(request.glAccountId(), request.accountCode(),
            request.accountName(), DebitCredit.valueOf(request.postBalance()),
            request.glAccountClassId());
        return ResponseEntity.status(HttpStatus.CREATED).body(new AccountResponse(
            account.glAccountId(), account.accountCode(), account.accountName(),
            account.postBalance().name(), account.glAccountClassId()));
    }

    @GetMapping("/accounts/{glAccountId}")
    ResponseEntity<AccountResponse> getAccount(@PathVariable String glAccountId) {
        return gl.getAccount(glAccountId)
            .map(a -> new AccountResponse(a.glAccountId(), a.accountCode(), a.accountName(),
                a.postBalance().name(), a.glAccountClassId()))
            .map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/periods")
    @Transactional
    ResponseEntity<PeriodResponse> createPeriod(@RequestBody CreatePeriodRequest request) {
        Period period = gl.createPeriod(request.periodId(), LocalDate.parse(request.fromDate()),
            LocalDate.parse(request.thruDate()));
        return toPeriodResponse(period, HttpStatus.CREATED);
    }

    @GetMapping("/periods/{periodId}")
    ResponseEntity<PeriodResponse> getPeriod(@PathVariable String periodId) {
        return gl.findPeriod(periodId).map(p -> toPeriodResponse(p, HttpStatus.OK).getBody())
            .map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    /** P5-F2-S1 AC: one-way close; afterwards postings in it are refused. */
    @PostMapping("/periods/{periodId}/close")
    @Transactional
    ResponseEntity<PeriodResponse> closePeriod(@PathVariable String periodId) {
        return guarded(() -> toPeriodResponse(gl.closePeriod(periodId), HttpStatus.OK).getBody());
    }

    @PostMapping("/transactions")
    @Transactional
    ResponseEntity<TransactionResponse> postTransaction(
            @RequestBody PostTransactionRequest request) {
        List<GlEntry> entries = request.entries().stream()
            .map(e -> new GlEntry(e.glAccountId(), e.amount(), DebitCredit.valueOf(
                e.debitCredit())))
            .toList();
        return guarded(() -> toResponse(
            gl.postTransaction(request.acctgTransId(), request.transType(),
                request.description(), request.periodId(), entries)));
    }

    @GetMapping("/transactions/{acctgTransId}")
    ResponseEntity<TransactionResponse> getTransaction(@PathVariable String acctgTransId) {
        return gl.find(acctgTransId).map(this::toResponse).map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/rules")
    @Transactional
    ResponseEntity<RuleResponse> createRule(@RequestBody CreateRuleRequest request) {
        PostingRule rule = new PostingRule(request.ruleId(), request.eventType(),
            request.debitAccountId(), request.creditAccountId(), request.enabled());
        rules.save(rule);
        return ResponseEntity.status(HttpStatus.CREATED).body(new RuleResponse(rule.ruleId(),
            rule.eventType(), rule.debitAccountId(), rule.creditAccountId(), rule.enabled()));
    }

    @GetMapping("/rules")
    List<RuleResponse> rules() {
        return rules.all().stream()
            .map(r -> new RuleResponse(r.ruleId(), r.eventType(), r.debitAccountId(),
                r.creditAccountId(), r.enabled()))
            .toList();
    }

    /** P5-F2-S2 AC: parked postings are visible for review, never dropped. */
    @GetMapping("/parked")
    List<String> parkedPostings() {
        return parked.parkedReasons();
    }

    /** P5-F2-S3 AC: trial balance over posted transactions. */
    @GetMapping("/trial-balance")
    GlService.TrialBalance trialBalance() {
        return gl.trialBalance();
    }

    private TransactionResponse toResponse(GlTransaction transaction) {
        return new TransactionResponse(transaction.acctgTransId(), transaction.transType(),
            transaction.description(), transaction.periodId(), transaction.isPosted(),
            transaction.entries().stream()
                .map(e -> new EntryResponse(e.glAccountId(), e.amount(),
                    e.debitCredit().name()))
                .toList());
    }

    private ResponseEntity<PeriodResponse> toPeriodResponse(Period period, HttpStatus status) {
        return ResponseEntity.status(status).body(new PeriodResponse(period.periodId(),
            period.fromDate().toString(), period.thruDate().toString(), period.isClosed()));
    }

    /** @return 409 with the typed error when a guard rejects the operation. */
    private <T> ResponseEntity<T> guarded(Supplier<T> action) {
        try {
            return ResponseEntity.ok(action.get());
        } catch (UnbalancedTransactionException | ClosedPeriodException
                | AlreadyPostedException | IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .header("X-Error-Reason", e.getClass().getSimpleName()).build();
        }
    }

    @ExceptionHandler(NoSuchElementException.class)
    ResponseEntity<String> notFound(Exception e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
    }
}
