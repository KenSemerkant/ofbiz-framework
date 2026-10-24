package workeffort.domain;

import java.time.LocalDate;

/**
 * P6-T2 WorkEffort aggregate (live slice of the monolith WorkEffort):
 * id, kind, name, tree parent and the forward-only status machine. Dates
 * are estimates and may be absent, but completion is never before start.
 * Calendar export needs both dates (see ICalendar).
 */
public final class WorkEffort {

    private final String workEffortId;
    private final WorkEffortType workEffortTypeId;
    private String workEffortName;
    private String parentWorkEffortId;
    private WorkEffortStatus statusId;
    private LocalDate estimatedStartDate;
    private LocalDate estimatedCompletionDate;

    public WorkEffort(String workEffortId, WorkEffortType workEffortTypeId,
            String workEffortName, String parentWorkEffortId, LocalDate estimatedStartDate,
            LocalDate estimatedCompletionDate) {
        if (workEffortId == null || workEffortId.isBlank()) {
            throw new IllegalArgumentException("workEffortId must not be blank");
        }
        if (workEffortTypeId == null) {
            throw new IllegalArgumentException("workEffortTypeId must not be null");
        }
        if (workEffortName == null || workEffortName.isBlank()) {
            throw new IllegalArgumentException("workEffortName must not be blank");
        }
        if (workEffortId.equals(parentWorkEffortId)) {
            throw new IllegalArgumentException("workEffortId must not be its own parent");
        }
        validateDates(estimatedStartDate, estimatedCompletionDate);
        this.workEffortId = workEffortId;
        this.workEffortTypeId = workEffortTypeId;
        this.workEffortName = workEffortName;
        this.parentWorkEffortId = parentWorkEffortId;
        this.statusId = WorkEffortStatus.CREATED;
        this.estimatedStartDate = estimatedStartDate;
        this.estimatedCompletionDate = estimatedCompletionDate;
    }

    /**
     * @throws IllegalStatusTransitionException on skips, backwards moves and
     *         anything out of a terminal state
     */
    public void changeStatus(WorkEffortStatus target) {
        if (target == null || !statusId.canTransitionTo(target)) {
            throw new IllegalStatusTransitionException(workEffortId, statusId, target);
        }
        statusId = target;
    }

    /** Full-replace update (PUT semantics); revalidates the date pair. */
    public void updateDetails(String name, String parentWorkEffortId,
            LocalDate estimatedStartDate, LocalDate estimatedCompletionDate) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("workEffortName must not be blank");
        }
        if (workEffortId.equals(parentWorkEffortId)) {
            throw new IllegalArgumentException("workEffortId must not be its own parent");
        }
        validateDates(estimatedStartDate, estimatedCompletionDate);
        this.workEffortName = name;
        this.parentWorkEffortId = parentWorkEffortId;
        this.estimatedStartDate = estimatedStartDate;
        this.estimatedCompletionDate = estimatedCompletionDate;
    }

    /** Both estimates present — the precondition for iCal export. */
    public boolean isCalendarReady() {
        return estimatedStartDate != null && estimatedCompletionDate != null;
    }

    /** Rehydration factory: persisted state is trusted. */
    public static WorkEffort rehydrate(String workEffortId, WorkEffortType workEffortTypeId,
            String workEffortName, String parentWorkEffortId, WorkEffortStatus statusId,
            LocalDate estimatedStartDate, LocalDate estimatedCompletionDate) {
        WorkEffort effort = new WorkEffort(workEffortId, workEffortTypeId, workEffortName,
            parentWorkEffortId, estimatedStartDate, estimatedCompletionDate);
        effort.statusId = statusId;
        return effort;
    }

    private static void validateDates(LocalDate estimatedStartDate,
            LocalDate estimatedCompletionDate) {
        if (estimatedStartDate != null && estimatedCompletionDate != null
                && estimatedCompletionDate.isBefore(estimatedStartDate)) {
            throw new IllegalArgumentException(
                "estimatedCompletionDate must not be before estimatedStartDate");
        }
    }

    public String workEffortId() {
        return workEffortId;
    }

    public WorkEffortType workEffortTypeId() {
        return workEffortTypeId;
    }

    public String workEffortName() {
        return workEffortName;
    }

    public String parentWorkEffortId() {
        return parentWorkEffortId;
    }

    public WorkEffortStatus statusId() {
        return statusId;
    }

    public LocalDate estimatedStartDate() {
        return estimatedStartDate;
    }

    public LocalDate estimatedCompletionDate() {
        return estimatedCompletionDate;
    }
}
