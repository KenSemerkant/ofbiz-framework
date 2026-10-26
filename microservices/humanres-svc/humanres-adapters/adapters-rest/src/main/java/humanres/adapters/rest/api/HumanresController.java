package humanres.adapters.rest.api;

import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import humanres.application.DuplicateEmploymentException;
import humanres.application.DuplicatePositionException;
import humanres.application.EmploymentService;
import humanres.application.OnboardingSaga;
import humanres.application.OnboardingSagaStore.SagaRow;
import humanres.application.PartyNotFoundException;
import humanres.application.PositionService;
import humanres.domain.EmplPosition;
import humanres.domain.Employment;

/**
 * P6-T3 REST edge: /api/v1/positions, /api/v1/employments and the
 * /api/v1/onboarding saga (typed 409/404 like finance-svc). Mutating
 * endpoints run in one adapter-layer tx so the aggregate + outbox row
 * commit together.
 */
@RestController
public class HumanresController {

    private final PositionService positions;
    private final EmploymentService employments;
    private final OnboardingSaga onboarding;

    public HumanresController(PositionService positions, EmploymentService employments,
            OnboardingSaga onboarding) {
        this.positions = positions;
        this.employments = employments;
        this.onboarding = onboarding;
    }

    public record CreatePositionRequest(String emplPositionId, String emplPositionTypeId,
            String reportingToEmplPositionId, String statusId) {
    }

    public record UpdatePositionRequest(String emplPositionTypeId,
            String reportingToEmplPositionId, String statusId) {
    }

    public record CreateEmploymentRequest(String employmentId, String partyId,
            String emplPositionId, String fromDate, String thruDate) {
    }

    public record UpdateEmploymentRequest(String thruDate) {
    }

    public record StartOnboardingRequest(String partyId, String emplPositionId) {
    }

    public record PositionResponse(String emplPositionId, String emplPositionTypeId,
            String reportingToEmplPositionId, String statusId) {
    }

    public record EmploymentResponse(String employmentId, String partyId, String emplPositionId,
            String fromDate, String thruDate) {
    }

    public record OnboardingResponse(String onboardingId, String partyId, String emplPositionId,
            String status, List<String> steps, String failedReason) {
    }

    @PostMapping("/api/v1/positions")
    @Transactional
    ResponseEntity<PositionResponse> createPosition(@RequestBody CreatePositionRequest request) {
        EmplPosition position = positions.create(request.emplPositionId(),
            request.emplPositionTypeId(), request.reportingToEmplPositionId(),
            request.statusId());
        return ResponseEntity.status(HttpStatus.CREATED).body(toPositionResponse(position));
    }

    /** P9-F6: position listing for the SPA HR area. */
    @GetMapping("/api/v1/positions")
    java.util.List<PositionResponse> listPositions() {
        return positions.all().stream().map(this::toPositionResponse).toList();
    }

    @GetMapping("/api/v1/positions/{emplPositionId}")
    ResponseEntity<PositionResponse> getPosition(@PathVariable String emplPositionId) {
        return positions.find(emplPositionId).map(this::toPositionResponse)
            .map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PutMapping("/api/v1/positions/{emplPositionId}")
    @Transactional
    ResponseEntity<PositionResponse> updatePosition(@PathVariable String emplPositionId,
            @RequestBody UpdatePositionRequest request) {
        EmplPosition position = positions.update(emplPositionId, request.emplPositionTypeId(),
            request.reportingToEmplPositionId(), request.statusId());
        return ResponseEntity.ok(toPositionResponse(position));
    }

    @PostMapping("/api/v1/employments")
    @Transactional
    ResponseEntity<EmploymentResponse> createEmployment(
            @RequestBody CreateEmploymentRequest request) {
        Employment employment = employments.create(request.employmentId(), request.partyId(),
            request.emplPositionId(), LocalDate.parse(request.fromDate()),
            request.thruDate() == null ? null : LocalDate.parse(request.thruDate()));
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(toEmploymentResponse(employment));
    }

    /** P9-F6: employment listing for the SPA HR area. */
    @GetMapping("/api/v1/employments")
    java.util.List<EmploymentResponse> listEmployments() {
        return employments.all().stream().map(this::toEmploymentResponse).toList();
    }

    @GetMapping("/api/v1/employments/{employmentId}")
    ResponseEntity<EmploymentResponse> getEmployment(@PathVariable String employmentId) {
        return employments.find(employmentId).map(this::toEmploymentResponse)
            .map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    /** End (or re-open with null) an employment: only the thru date moves. */
    @PutMapping("/api/v1/employments/{employmentId}")
    @Transactional
    ResponseEntity<EmploymentResponse> updateEmployment(@PathVariable String employmentId,
            @RequestBody UpdateEmploymentRequest request) {
        Employment employment = employments.updateThruDate(employmentId,
            request.thruDate() == null ? null : LocalDate.parse(request.thruDate()));
        return ResponseEntity.ok(toEmploymentResponse(employment));
    }

    /**
     * P6-T3 onboarding saga: EMPLOYEE role via party-svc, then the
     * employment. Returns the saga state — a party-svc outage surfaces as
     * FAILED (retryable), an unknown party as 404.
     */
    @PostMapping("/api/v1/onboarding")
    @Transactional
    ResponseEntity<OnboardingResponse> startOnboarding(
            @RequestBody StartOnboardingRequest request) {
        SagaRow saga = onboarding.start(request.partyId(), request.emplPositionId());
        return ResponseEntity.status(HttpStatus.CREATED).body(toOnboardingResponse(saga));
    }

    @GetMapping("/api/v1/onboarding/{onboardingId}")
    ResponseEntity<OnboardingResponse> getOnboarding(@PathVariable String onboardingId) {
        return onboarding.find(onboardingId).map(this::toOnboardingResponse)
            .map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    /** Re-drive a FAILED (or stuck) onboarding; completed steps are no-ops. */
    @PostMapping("/api/v1/onboarding/{onboardingId}/retry")
    @Transactional
    ResponseEntity<OnboardingResponse> retryOnboarding(@PathVariable String onboardingId) {
        return ResponseEntity.ok(toOnboardingResponse(onboarding.retry(onboardingId)));
    }

    private PositionResponse toPositionResponse(EmplPosition position) {
        return new PositionResponse(position.emplPositionId(), position.emplPositionTypeId(),
            position.reportingToEmplPositionId(), position.statusId());
    }

    private EmploymentResponse toEmploymentResponse(Employment employment) {
        return new EmploymentResponse(employment.employmentId(), employment.partyId(),
            employment.emplPositionId(), employment.fromDate().toString(),
            employment.thruDate() == null ? null : employment.thruDate().toString());
    }

    private OnboardingResponse toOnboardingResponse(SagaRow saga) {
        return new OnboardingResponse(saga.onboardingId(), saga.partyId(),
            saga.emplPositionId(), saga.status().name(), saga.steps(), saga.failedReason());
    }

    @ExceptionHandler({NoSuchElementException.class, PartyNotFoundException.class})
    ResponseEntity<String> notFound(Exception e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
    }

    /** @return 409 with the typed reason when an id is recreated. */
    @ExceptionHandler({DuplicatePositionException.class, DuplicateEmploymentException.class})
    ResponseEntity<String> conflict(Exception e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .header("X-Error-Reason", e.getClass().getSimpleName()).body(e.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<String> badRequest(Exception e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
    }
}
