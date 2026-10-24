package workeffort.application;

import workeffort.domain.Timesheet;
import workeffort.domain.WorkEffort;

/** Event-payload port: the application layer never touches Jackson. */
public interface WorkEffortSerializer {

    String serializeEffort(WorkEffort effort);

    String serializeTimesheetSubmitted(Timesheet timesheet);
}
