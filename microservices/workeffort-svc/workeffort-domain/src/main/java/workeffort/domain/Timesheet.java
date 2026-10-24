package workeffort.domain;

import java.time.LocalDate;
import java.util.List;

/**
 * P6-T2 Timesheet aggregate: a party's booking window with inline TimeEntry
 * lines. DRAFT → SUBMITTED is one-way; only a draft can be edited.
 */
public final class Timesheet {

    private final String timesheetId;
    private String partyId;
    private LocalDate fromDate;
    private LocalDate thruDate;
    private List<TimesheetEntry> entries;
    private TimesheetStatus statusId;

    public Timesheet(String timesheetId, String partyId, LocalDate fromDate,
            LocalDate thruDate, List<TimesheetEntry> entries) {
        if (timesheetId == null || timesheetId.isBlank()) {
            throw new IllegalArgumentException("timesheetId must not be blank");
        }
        if (partyId == null || partyId.isBlank()) {
            throw new IllegalArgumentException("partyId must not be blank");
        }
        validateDates(fromDate, thruDate);
        this.timesheetId = timesheetId;
        this.partyId = partyId;
        this.fromDate = fromDate;
        this.thruDate = thruDate;
        this.entries = List.copyOf(entries == null ? List.of() : entries);
        this.statusId = TimesheetStatus.DRAFT;
    }

    /**
     * @throws TimesheetAlreadySubmittedException on resubmission — the
     *         one-way gate the REST layer maps to a typed 409
     */
    public void submit() {
        if (statusId != TimesheetStatus.DRAFT) {
            throw new TimesheetAlreadySubmittedException(timesheetId);
        }
        statusId = TimesheetStatus.SUBMITTED;
    }

    /** Full-replace update; only while still a draft. */
    public void updateDetails(String partyId, LocalDate fromDate, LocalDate thruDate,
            List<TimesheetEntry> entries) {
        if (partyId == null || partyId.isBlank()) {
            throw new IllegalArgumentException("partyId must not be blank");
        }
        if (statusId != TimesheetStatus.DRAFT) {
            throw new TimesheetAlreadySubmittedException(timesheetId);
        }
        validateDates(fromDate, thruDate);
        this.partyId = partyId;
        this.fromDate = fromDate;
        this.thruDate = thruDate;
        this.entries = List.copyOf(entries == null ? List.of() : entries);
    }

    /** Rehydration factory: persisted state is trusted. */
    public static Timesheet rehydrate(String timesheetId, String partyId, LocalDate fromDate,
            LocalDate thruDate, List<TimesheetEntry> entries, TimesheetStatus statusId) {
        Timesheet timesheet = new Timesheet(timesheetId, partyId, fromDate, thruDate, entries);
        timesheet.statusId = statusId;
        return timesheet;
    }

    private static void validateDates(LocalDate fromDate, LocalDate thruDate) {
        if (fromDate == null || thruDate == null || thruDate.isBefore(fromDate)) {
            throw new IllegalArgumentException("thruDate must not be before fromDate");
        }
    }

    public String timesheetId() {
        return timesheetId;
    }

    public String partyId() {
        return partyId;
    }

    public LocalDate fromDate() {
        return fromDate;
    }

    public LocalDate thruDate() {
        return thruDate;
    }

    public List<TimesheetEntry> entries() {
        return entries;
    }

    public TimesheetStatus statusId() {
        return statusId;
    }
}
