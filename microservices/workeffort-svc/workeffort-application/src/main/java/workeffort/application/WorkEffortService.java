package workeffort.application;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import workeffort.domain.ICalendar;
import workeffort.domain.IllegalStatusTransitionException;
import workeffort.domain.WorkEffort;
import workeffort.domain.WorkEffortStatus;
import workeffort.domain.WorkEffortType;

/**
 * P6-T2 WorkEffort use case: CRUD, the one-level tree query and the two
 * iCal exports. Every created/updated effort lands one outbox event in
 * the same transaction (ADR-009).
 */
public class WorkEffortService {

    public static final String EFFORT_CREATED = "workeffort.effort.created.v1";
    public static final String EFFORT_UPDATED = "workeffort.effort.updated.v1";

    private final WorkEffortStore efforts;
    private final OutboxWriter outbox;
    private final WorkEffortSerializer serializer;

    public WorkEffortService(WorkEffortStore efforts, OutboxWriter outbox,
            WorkEffortSerializer serializer) {
        this.efforts = efforts;
        this.outbox = outbox;
        this.serializer = serializer;
    }

    public Optional<WorkEffort> find(String workEffortId) {
        return efforts.find(workEffortId);
    }

    public List<WorkEffort> all() {
        return efforts.all();
    }

    /** @throws NoSuchElementException when the referenced parent does not exist */
    public WorkEffort create(String workEffortId, WorkEffortType workEffortTypeId,
            String workEffortName, String parentWorkEffortId,
            java.time.LocalDate estimatedStartDate, java.time.LocalDate estimatedCompletionDate) {
        if (parentWorkEffortId != null && efforts.find(parentWorkEffortId).isEmpty()) {
            throw new NoSuchElementException("no work effort " + parentWorkEffortId);
        }
        WorkEffort effort = new WorkEffort(workEffortId, workEffortTypeId, workEffortName,
            parentWorkEffortId, estimatedStartDate, estimatedCompletionDate);
        efforts.save(effort);
        outbox.append(EFFORT_CREATED, workEffortId, serializer.serializeEffort(effort));
        return effort;
    }

    /**
     * @throws NoSuchElementException           when the effort is unknown
     * @throws IllegalStatusTransitionException when the new date pair is invalid
     */
    public WorkEffort update(String workEffortId, String workEffortName,
            String parentWorkEffortId, java.time.LocalDate estimatedStartDate,
            java.time.LocalDate estimatedCompletionDate) {
        if (parentWorkEffortId != null && !parentWorkEffortId.equals(workEffortId)
                && efforts.find(parentWorkEffortId).isEmpty()) {
            throw new NoSuchElementException("no work effort " + parentWorkEffortId);
        }
        WorkEffort effort = efforts.find(workEffortId)
            .orElseThrow(() -> new NoSuchElementException("no work effort " + workEffortId));
        effort.updateDetails(workEffortName, parentWorkEffortId, estimatedStartDate,
            estimatedCompletionDate);
        efforts.save(effort);
        outbox.append(EFFORT_UPDATED, workEffortId, serializer.serializeEffort(effort));
        return effort;
    }

    /** @throws IllegalStatusTransitionException via the domain guard */
    public WorkEffort changeStatus(String workEffortId, WorkEffortStatus target) {
        WorkEffort effort = efforts.find(workEffortId)
            .orElseThrow(() -> new NoSuchElementException("no work effort " + workEffortId));
        effort.changeStatus(target);
        efforts.save(effort);
        outbox.append(EFFORT_UPDATED, workEffortId, serializer.serializeEffort(effort));
        return effort;
    }

    public void delete(String workEffortId) {
        efforts.delete(workEffortId);
    }

    public List<WorkEffort> findByParent(String parentWorkEffortId) {
        return efforts.findByParent(parentWorkEffortId);
    }

    /** The CG: every calendar-ready effort as one RFC 5545 feed. */
    public String calendarFeed() {
        return ICalendar.render(efforts.findAllCalendarReady().stream()
            .map(e -> new ICalendar.CalendarEvent(e.workEffortId(), e.workEffortName(),
                e.estimatedStartDate(), e.estimatedCompletionDate()))
            .toList(), Instant.now());
    }

    /**
     * @throws NoSuchElementException  when the effort is unknown
     * @throws IllegalStateException   when it lacks the dates a VEVENT needs
     */
    public String calendarFor(String workEffortId) {
        WorkEffort effort = efforts.find(workEffortId)
            .orElseThrow(() -> new NoSuchElementException("no work effort " + workEffortId));
        if (!effort.isCalendarReady()) {
            throw new IllegalStateException(
                "work effort %s has no calendar dates".formatted(workEffortId));
        }
        return ICalendar.render(List.of(new ICalendar.CalendarEvent(effort.workEffortId(),
            effort.workEffortName(), effort.estimatedStartDate(),
            effort.estimatedCompletionDate())), Instant.now());
    }
}
