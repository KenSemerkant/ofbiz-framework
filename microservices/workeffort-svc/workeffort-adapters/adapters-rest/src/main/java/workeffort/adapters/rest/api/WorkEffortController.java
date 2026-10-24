package workeffort.adapters.rest.api;

import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.function.Supplier;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import workeffort.application.WorkEffortService;
import workeffort.domain.IllegalStatusTransitionException;
import workeffort.domain.WorkEffort;
import workeffort.domain.WorkEffortStatus;
import workeffort.domain.WorkEffortType;

/**
 * P6-T2 REST edge: /api/v1/workefforts — CRUD, the one-level tree query
 * (?parent=), the forward-only status transitions and the two iCal
 * exports (the CG). Guard violations map to typed 409s; mutating
 * endpoints run in one adapter-layer tx so the aggregate + outbox row
 * commit together.
 */
@RestController
@RequestMapping("/api/v1/workefforts")
public class WorkEffortController {

    private final WorkEffortService efforts;

    public WorkEffortController(WorkEffortService efforts) {
        this.efforts = efforts;
    }

    public record CreateEffortRequest(String workEffortId, String workEffortTypeId,
            String workEffortName, String parentWorkEffortId, String estimatedStartDate,
            String estimatedCompletionDate) {
    }

    public record UpdateEffortRequest(String workEffortName, String parentWorkEffortId,
            String estimatedStartDate, String estimatedCompletionDate) {
    }

    public record ChangeStatusRequest(String statusId) {
    }

    public record EffortResponse(String workEffortId, String workEffortTypeId,
            String workEffortName, String parentWorkEffortId, String statusId,
            String estimatedStartDate, String estimatedCompletionDate) {
    }

    @PostMapping
    @Transactional
    ResponseEntity<EffortResponse> create(@RequestBody CreateEffortRequest request) {
        WorkEffort effort = efforts.create(request.workEffortId(),
            WorkEffortType.valueOf(request.workEffortTypeId()), request.workEffortName(),
            request.parentWorkEffortId(), parseDate(request.estimatedStartDate()),
            parseDate(request.estimatedCompletionDate()));
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(effort));
    }

    @GetMapping
    List<EffortResponse> list(@RequestParam(name = "parent", required = false) String parent) {
        List<WorkEffort> found = parent == null ? efforts.all() : efforts.findByParent(parent);
        return found.stream().map(this::toResponse).toList();
    }

    @GetMapping("/calendar.ics")
    ResponseEntity<String> calendarFeed() {
        return ResponseEntity.ok().contentType(MediaType.valueOf("text/calendar"))
            .body(efforts.calendarFeed());
    }

    @GetMapping("/{workEffortId}/calendar.ics")
    ResponseEntity<String> calendarFor(@PathVariable String workEffortId) {
        try {
            return ResponseEntity.ok().contentType(MediaType.valueOf("text/calendar"))
                .body(efforts.calendarFor(workEffortId));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .header("X-Error-Reason", e.getClass().getSimpleName()).build();
        }
    }

    @GetMapping("/{workEffortId}")
    ResponseEntity<EffortResponse> get(@PathVariable String workEffortId) {
        return efforts.find(workEffortId).map(e -> ResponseEntity.ok(toResponse(e)))
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PutMapping("/{workEffortId}")
    @Transactional
    ResponseEntity<EffortResponse> update(@PathVariable String workEffortId,
            @RequestBody UpdateEffortRequest request) {
        return guarded(() -> toResponse(efforts.update(workEffortId,
            request.workEffortName(), request.parentWorkEffortId(),
            parseDate(request.estimatedStartDate()),
            parseDate(request.estimatedCompletionDate()))));
    }

    /** P6-T2 AC: forward-only machine; illegal moves are typed 409s. */
    @PostMapping("/{workEffortId}/status")
    @Transactional
    ResponseEntity<EffortResponse> changeStatus(@PathVariable String workEffortId,
            @RequestBody ChangeStatusRequest request) {
        return guarded(() -> toResponse(efforts.changeStatus(workEffortId,
            WorkEffortStatus.valueOf(request.statusId()))));
    }

    @DeleteMapping("/{workEffortId}")
    @Transactional
    ResponseEntity<Void> delete(@PathVariable String workEffortId) {
        efforts.delete(workEffortId);
        return ResponseEntity.noContent().build();
    }

    private static LocalDate parseDate(String date) {
        return date == null ? null : LocalDate.parse(date);
    }

    private EffortResponse toResponse(WorkEffort effort) {
        return new EffortResponse(effort.workEffortId(), effort.workEffortTypeId().name(),
            effort.workEffortName(), effort.parentWorkEffortId(), effort.statusId().name(),
            effort.estimatedStartDate() == null ? null
                : effort.estimatedStartDate().toString(),
            effort.estimatedCompletionDate() == null ? null
                : effort.estimatedCompletionDate().toString());
    }

    /** @return 409 with the typed error when a guard rejects the operation. */
    private <T> ResponseEntity<T> guarded(Supplier<T> action) {
        try {
            return ResponseEntity.ok(action.get());
        } catch (IllegalStatusTransitionException | IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .header("X-Error-Reason", e.getClass().getSimpleName()).build();
        }
    }

    @ExceptionHandler(NoSuchElementException.class)
    ResponseEntity<String> notFound(Exception e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
    }
}
