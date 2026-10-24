package workeffort.adapters.rest.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import workeffort.domain.WorkEffortStatus;
import workeffort.domain.WorkEffortType;

/** work_efforts row (P6-T2, WorkEffort live slice). */
@Entity
@Table(name = "work_efforts")
public class WorkEffortEntity {

    @Id
    @Column(name = "work_effort_id")
    private String workEffortId;

    @Enumerated(EnumType.STRING)
    @Column(name = "work_effort_type_id", nullable = false)
    private WorkEffortType workEffortTypeId;

    @Column(name = "work_effort_name", nullable = false)
    private String workEffortName;

    @Column(name = "parent_work_effort_id")
    private String parentWorkEffortId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status_id", nullable = false)
    private WorkEffortStatus statusId;

    @Column(name = "estimated_start_date")
    private LocalDate estimatedStartDate;

    @Column(name = "estimated_completion_date")
    private LocalDate estimatedCompletionDate;

    @Column(name = "created_at", nullable = false)
    private LocalDate createdAt = LocalDate.now();

    public WorkEffortEntity() {
    }

    public WorkEffortEntity(String workEffortId, WorkEffortType workEffortTypeId,
            String workEffortName, String parentWorkEffortId, WorkEffortStatus statusId,
            LocalDate estimatedStartDate, LocalDate estimatedCompletionDate) {
        this.workEffortId = workEffortId;
        this.workEffortTypeId = workEffortTypeId;
        this.workEffortName = workEffortName;
        this.parentWorkEffortId = parentWorkEffortId;
        this.statusId = statusId;
        this.estimatedStartDate = estimatedStartDate;
        this.estimatedCompletionDate = estimatedCompletionDate;
    }

    public String getWorkEffortId() {
        return workEffortId;
    }

    public WorkEffortType getWorkEffortTypeId() {
        return workEffortTypeId;
    }

    public String getWorkEffortName() {
        return workEffortName;
    }

    public String getParentWorkEffortId() {
        return parentWorkEffortId;
    }

    public WorkEffortStatus getStatusId() {
        return statusId;
    }

    public LocalDate getEstimatedStartDate() {
        return estimatedStartDate;
    }

    public LocalDate getEstimatedCompletionDate() {
        return estimatedCompletionDate;
    }

    public LocalDate getCreatedAt() {
        return createdAt;
    }
}
