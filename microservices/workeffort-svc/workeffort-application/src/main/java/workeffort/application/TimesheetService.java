package workeffort.application;

import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import workeffort.domain.Timesheet;
import workeffort.domain.TimesheetEntry;

/**
 * P6-T2 Timesheet use case: CRUD and the one-way DRAFT → SUBMITTED
 * submission with its outbox event. Entries reference WorkEffort ids —
 * validated at the boundary, stored as IDs only (ADR-007).
 */
public class TimesheetService {

    public static final String TIMESHEET_SUBMITTED = "workeffort.timesheet.submitted.v1";

    private final TimesheetStore timesheets;
    private final WorkEffortStore efforts;
    private final OutboxWriter outbox;
    private final WorkEffortSerializer serializer;

    public TimesheetService(TimesheetStore timesheets, WorkEffortStore efforts,
            OutboxWriter outbox, WorkEffortSerializer serializer) {
        this.timesheets = timesheets;
        this.efforts = efforts;
        this.outbox = outbox;
        this.serializer = serializer;
    }

    public Optional<Timesheet> find(String timesheetId) {
        return timesheets.find(timesheetId);
    }

    public List<Timesheet> all() {
        return timesheets.all();
    }

    /** @throws NoSuchElementException when an entry names an unknown effort */
    public Timesheet create(String timesheetId, String partyId, LocalDate fromDate,
            LocalDate thruDate, List<TimesheetEntry> entries) {
        validateEntries(entries);
        Timesheet timesheet = new Timesheet(timesheetId, partyId, fromDate, thruDate, entries);
        timesheets.save(timesheet);
        return timesheet;
    }

    /**
     * @throws NoSuchElementException            when the sheet or an entry's
     *                                          effort is unknown
     * @throws workeffort.domain.TimesheetAlreadySubmittedException when submitted
     */
    public Timesheet update(String timesheetId, String partyId, LocalDate fromDate,
            LocalDate thruDate, List<TimesheetEntry> entries) {
        validateEntries(entries);
        Timesheet timesheet = timesheets.find(timesheetId)
            .orElseThrow(() -> new NoSuchElementException("no timesheet " + timesheetId));
        timesheet.updateDetails(partyId, fromDate, thruDate, entries);
        timesheets.save(timesheet);
        return timesheet;
    }

    /** @throws workeffort.domain.TimesheetAlreadySubmittedException on resubmit */
    public Timesheet submit(String timesheetId) {
        Timesheet timesheet = timesheets.find(timesheetId)
            .orElseThrow(() -> new NoSuchElementException("no timesheet " + timesheetId));
        timesheet.submit();
        timesheets.save(timesheet);
        outbox.append(TIMESHEET_SUBMITTED, timesheetId,
            serializer.serializeTimesheetSubmitted(timesheet));
        return timesheet;
    }

    public void delete(String timesheetId) {
        timesheets.delete(timesheetId);
    }

    private void validateEntries(List<TimesheetEntry> entries) {
        if (entries == null) {
            return;
        }
        for (TimesheetEntry entry : entries) {
            if (efforts.find(entry.workEffortId()).isEmpty()) {
                throw new NoSuchElementException("no work effort " + entry.workEffortId());
            }
        }
    }
}
