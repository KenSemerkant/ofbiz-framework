package workeffort.domain;

/** P6-T2: submission is one-way — a submitted timesheet never reopens. */
public final class TimesheetAlreadySubmittedException extends RuntimeException {

    public TimesheetAlreadySubmittedException(String timesheetId) {
        super("timesheet %s is already submitted".formatted(timesheetId));
    }
}
