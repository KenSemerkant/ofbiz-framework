package workeffort.adapters.rest.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.function.Supplier;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import workeffort.application.TimesheetService;
import workeffort.domain.Timesheet;
import workeffort.domain.TimesheetAlreadySubmittedException;
import workeffort.domain.TimesheetEntry;

/**
 * P6-T2 REST edge: /api/v1/timesheets — CRUD plus the one-way
 * DRAFT → SUBMITTED submission. Resubmission and edits after submission
 * are typed 409s; the submission lands its outbox event in the same tx.
 */
@RestController
@RequestMapping("/api/v1/timesheets")
public class TimesheetController {

    private final TimesheetService timesheets;

    public TimesheetController(TimesheetService timesheets) {
        this.timesheets = timesheets;
    }

    public record EntryRequest(String workEffortId, BigDecimal hours, String rateTypeId) {
    }

    public record CreateTimesheetRequest(String timesheetId, String partyId, String fromDate,
            String thruDate, List<EntryRequest> entries) {
    }

    public record UpdateTimesheetRequest(String partyId, String fromDate, String thruDate,
            List<EntryRequest> entries) {
    }

    public record EntryResponse(String workEffortId, BigDecimal hours, String rateTypeId) {
    }

    public record TimesheetResponse(String timesheetId, String partyId, String fromDate,
            String thruDate, String statusId, List<EntryResponse> entries) {
    }

    @PostMapping
    @Transactional
    ResponseEntity<TimesheetResponse> create(@RequestBody CreateTimesheetRequest request) {
        Timesheet timesheet = timesheets.create(request.timesheetId(), request.partyId(),
            LocalDate.parse(request.fromDate()), LocalDate.parse(request.thruDate()),
            toEntries(request.entries()));
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(timesheet));
    }

    @GetMapping
    List<TimesheetResponse> list() {
        return timesheets.all().stream().map(this::toResponse).toList();
    }

    @GetMapping("/{timesheetId}")
    ResponseEntity<TimesheetResponse> get(@PathVariable String timesheetId) {
        return timesheets.find(timesheetId).map(t -> ResponseEntity.ok(toResponse(t)))
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PutMapping("/{timesheetId}")
    @Transactional
    ResponseEntity<TimesheetResponse> update(@PathVariable String timesheetId,
            @RequestBody UpdateTimesheetRequest request) {
        return guarded(() -> toResponse(timesheets.update(timesheetId, request.partyId(),
            LocalDate.parse(request.fromDate()), LocalDate.parse(request.thruDate()),
            toEntries(request.entries()))));
    }

    /** P6-T2 AC: DRAFT → SUBMITTED exactly once. */
    @PostMapping("/{timesheetId}/submit")
    @Transactional
    ResponseEntity<TimesheetResponse> submit(@PathVariable String timesheetId) {
        return guarded(() -> toResponse(timesheets.submit(timesheetId)));
    }

    @DeleteMapping("/{timesheetId}")
    @Transactional
    ResponseEntity<Void> delete(@PathVariable String timesheetId) {
        timesheets.delete(timesheetId);
        return ResponseEntity.noContent().build();
    }

    private static List<TimesheetEntry> toEntries(List<EntryRequest> entries) {
        return entries == null ? null
            : entries.stream()
                .map(e -> new TimesheetEntry(e.workEffortId(), e.hours(), e.rateTypeId()))
                .toList();
    }

    private TimesheetResponse toResponse(Timesheet timesheet) {
        return new TimesheetResponse(timesheet.timesheetId(), timesheet.partyId(),
            timesheet.fromDate().toString(), timesheet.thruDate().toString(),
            timesheet.statusId().name(),
            timesheet.entries().stream()
                .map(e -> new EntryResponse(e.workEffortId(), e.hours(), e.rateTypeId()))
                .toList());
    }

    /** @return 409 with the typed error when a guard rejects the operation. */
    private <T> ResponseEntity<T> guarded(Supplier<T> action) {
        try {
            return ResponseEntity.ok(action.get());
        } catch (TimesheetAlreadySubmittedException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .header("X-Error-Reason", e.getClass().getSimpleName()).build();
        }
    }

    @ExceptionHandler(NoSuchElementException.class)
    ResponseEntity<String> notFound(Exception e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
    }
}
