package workeffort.adapters.rest.persistence;

import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import workeffort.application.TimesheetStore;
import workeffort.domain.Timesheet;

/** JPA adapter for the TimesheetStore port. */
@Repository
public class JpaTimesheetStore implements TimesheetStore {

    private final TimesheetRepository timesheets;

    public JpaTimesheetStore(TimesheetRepository timesheets) {
        this.timesheets = timesheets;
    }

    @Override
    public Optional<Timesheet> find(String timesheetId) {
        return timesheets.findById(timesheetId).map(this::toDomain);
    }

    @Override
    public void save(Timesheet timesheet) {
        timesheets.save(new TimesheetEntity(timesheet.timesheetId(), timesheet.partyId(),
            timesheet.fromDate(), timesheet.thruDate(), timesheet.statusId(),
            TimesheetEntryJson.serialize(timesheet.entries())));
    }

    @Override
    public void delete(String timesheetId) {
        timesheets.deleteById(timesheetId);
    }

    @Override
    public List<Timesheet> all() {
        return timesheets.findAll().stream().map(this::toDomain).toList();
    }

    private Timesheet toDomain(TimesheetEntity entity) {
        return Timesheet.rehydrate(entity.getTimesheetId(), entity.getPartyId(),
            entity.getFromDate(), entity.getThruDate(),
            TimesheetEntryJson.deserialize(entity.getEntries()), entity.getStatusId());
    }
}
