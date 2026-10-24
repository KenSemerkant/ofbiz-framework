package workeffort.application;

import java.util.List;
import java.util.Optional;
import workeffort.domain.Timesheet;

/** Persistence port for the Timesheet aggregate. */
public interface TimesheetStore {

    Optional<Timesheet> find(String timesheetId);

    void save(Timesheet timesheet);

    void delete(String timesheetId);

    List<Timesheet> all();
}
