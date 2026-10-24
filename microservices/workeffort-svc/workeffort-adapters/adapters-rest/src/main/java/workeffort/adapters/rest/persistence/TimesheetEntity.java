package workeffort.adapters.rest.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import workeffort.domain.TimesheetStatus;

/** timesheets row: entries inline as JSON (inline-JSON precedent). */
@Entity
@Table(name = "timesheets")
public class TimesheetEntity {

    @Id
    @Column(name = "timesheet_id")
    private String timesheetId;

    @Column(name = "party_id", nullable = false)
    private String partyId;

    @Column(name = "from_date", nullable = false)
    private LocalDate fromDate;

    @Column(name = "thru_date", nullable = false)
    private LocalDate thruDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status_id", nullable = false)
    private TimesheetStatus statusId;

    @Column(nullable = false)
    private String entries;

    @Column(name = "created_at", nullable = false)
    private LocalDate createdAt = LocalDate.now();

    public TimesheetEntity() {
    }

    public TimesheetEntity(String timesheetId, String partyId, LocalDate fromDate,
            LocalDate thruDate, TimesheetStatus statusId, String entries) {
        this.timesheetId = timesheetId;
        this.partyId = partyId;
        this.fromDate = fromDate;
        this.thruDate = thruDate;
        this.statusId = statusId;
        this.entries = entries;
    }

    public String getTimesheetId() {
        return timesheetId;
    }

    public String getPartyId() {
        return partyId;
    }

    public LocalDate getFromDate() {
        return fromDate;
    }

    public LocalDate getThruDate() {
        return thruDate;
    }

    public TimesheetStatus getStatusId() {
        return statusId;
    }

    public String getEntries() {
        return entries;
    }

    public LocalDate getCreatedAt() {
        return createdAt;
    }
}
