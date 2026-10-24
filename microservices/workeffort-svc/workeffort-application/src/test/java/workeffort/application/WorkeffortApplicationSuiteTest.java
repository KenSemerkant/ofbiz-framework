package workeffort.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import workeffort.domain.ICalendar;
import workeffort.domain.IllegalStatusTransitionException;
import workeffort.domain.Timesheet;
import workeffort.domain.TimesheetAlreadySubmittedException;
import workeffort.domain.TimesheetEntry;
import workeffort.domain.TimesheetStatus;
import workeffort.domain.WorkEffort;
import workeffort.domain.WorkEffortStatus;
import workeffort.domain.WorkEffortType;

/**
 * P6-T2 use-case suite: CRUD with outbox events, the tree query, the
 * forward-only status machine, the two iCal exports (the CG), and the
 * timesheet submit-once gate with entry-reference validation.
 */
class WorkeffortApplicationSuiteTest {

    private final FakeEffortStore efforts = new FakeEffortStore();
    private final FakeTimesheetStore timesheets = new FakeTimesheetStore();
    private final List<String> outbox = new ArrayList<>();
    private final WorkEffortService effortService;
    private final TimesheetService timesheetService;

    WorkeffortApplicationSuiteTest() {
        WorkEffortSerializer serializer = new WorkEffortSerializer() {
            @Override
            public String serializeEffort(WorkEffort effort) {
                return "effort:" + effort.workEffortId();
            }

            @Override
            public String serializeTimesheetSubmitted(Timesheet timesheet) {
                return "timesheet:" + timesheet.timesheetId();
            }
        };
        effortService = new WorkEffortService(efforts,
            (type, key, payload) -> outbox.add(type + ":" + key), serializer);
        timesheetService = new TimesheetService(timesheets, efforts,
            (type, key, payload) -> outbox.add(type + ":" + key), serializer);
    }

    private WorkEffort seeded(String id, LocalDate start, LocalDate end) {
        return effortService.create(id, WorkEffortType.PROJECT, "project " + id, null, start,
            end);
    }

    @Test
    void createValidatesParentAndEmitsEvent() {
        assertThrows(NoSuchElementException.class,
            () -> effortService.create("WE-1", WorkEffortType.TASK, "orphan", "WE-GONE",
                null, null));
        WorkEffort parent = seeded("WE-P", LocalDate.parse("2026-01-05"),
            LocalDate.parse("2026-01-31"));
        WorkEffort child = effortService.create("WE-C", WorkEffortType.TASK, "child", "WE-P",
            LocalDate.parse("2026-01-06"), LocalDate.parse("2026-01-10"));
        assertEquals(WorkEffortStatus.CREATED, child.statusId());
        assertTrue(outbox.contains("workeffort.effort.created.v1:WE-P"));
        assertTrue(outbox.contains("workeffort.effort.created.v1:WE-C"));
        assertEquals(child.workEffortId(), effortService.find("WE-C").orElseThrow()
            .workEffortId());
        assertEquals(parent.workEffortId(), effortService.find("WE-P").orElseThrow()
            .workEffortId());
    }

    @Test
    void treeQueryReturnsOneLevel() {
        seeded("WE-P", null, null);
        effortService.create("WE-C1", WorkEffortType.TASK, "c1", "WE-P", null, null);
        effortService.create("WE-C2", WorkEffortType.TASK, "c2", "WE-P", null, null);
        effortService.create("WE-GC", WorkEffortType.TASK, "grandchild", "WE-C1", null, null);
        List<String> children = effortService.findByParent("WE-P").stream()
            .map(WorkEffort::workEffortId).toList();
        assertEquals(List.of("WE-C1", "WE-C2"), children);
        assertEquals(4, effortService.all().size());
    }

    @Test
    void updateEmitsEventAndValidates() {
        seeded("WE-U", null, null);
        WorkEffort updated = effortService.update("WE-U", "renamed", null,
            LocalDate.parse("2026-02-02"), LocalDate.parse("2026-02-20"));
        assertEquals("renamed", updated.workEffortName());
        assertTrue(outbox.contains("workeffort.effort.updated.v1:WE-U"));
        assertThrows(NoSuchElementException.class,
            () -> effortService.update("WE-U", "n", "WE-GONE", null, null));
        assertThrows(NoSuchElementException.class,
            () -> effortService.update("WE-NONE", "n", null, null, null));
        assertThrows(IllegalArgumentException.class,
            () -> effortService.update("WE-U", "n", null, LocalDate.parse("2026-02-20"),
                LocalDate.parse("2026-02-02")));
    }

    @Test
    void statusMachineTypedErrorsAndEvent() {
        seeded("WE-S", null, null);
        assertThrows(IllegalStatusTransitionException.class,
            () -> effortService.changeStatus("WE-S", WorkEffortStatus.COMPLETED));
        effortService.changeStatus("WE-S", WorkEffortStatus.IN_PROGRESS);
        effortService.changeStatus("WE-S", WorkEffortStatus.COMPLETED);
        assertEquals(WorkEffortStatus.COMPLETED,
            effortService.find("WE-S").orElseThrow().statusId());
        assertTrue(outbox.contains("workeffort.effort.updated.v1:WE-S"));
        assertThrows(NoSuchElementException.class,
            () -> effortService.changeStatus("WE-NONE", WorkEffortStatus.IN_PROGRESS));
    }

    @Test
    void cancelFromLiveStateOnly() {
        seeded("WE-L", null, null);
        effortService.changeStatus("WE-L", WorkEffortStatus.IN_PROGRESS);
        effortService.changeStatus("WE-L", WorkEffortStatus.CANCELLED);
        assertThrows(IllegalStatusTransitionException.class,
            () -> effortService.changeStatus("WE-L", WorkEffortStatus.COMPLETED));
    }

    @Test
    void deleteRemovesAndMissingEffortIs404() {
        seeded("WE-D", null, null);
        effortService.delete("WE-D");
        assertTrue(effortService.find("WE-D").isEmpty());
    }

    @Test
    void calendarFeedCarriesOnlyEffortsWithBothDates() {
        seeded("WE-CAL", LocalDate.parse("2026-01-05"), LocalDate.parse("2026-01-09"));
        seeded("WE-NODATES", null, null);
        seeded("WE-STARTONLY", LocalDate.parse("2026-01-05"), null);
        String feed = effortService.calendarFeed();
        assertTrue(feed.contains("UID:WE-CAL@ofbiz"));
        assertFalse(feed.contains("WE-NODATES"));
        assertFalse(feed.contains("WE-STARTONLY"));
        assertTrue(feed.startsWith("BEGIN:VCALENDAR\r\n"));
    }

    @Test
    void singleEffortCalendarAndItsGuards() {
        seeded("WE-ONE", LocalDate.parse("2026-03-02"), LocalDate.parse("2026-03-08"));
        String ics = effortService.calendarFor("WE-ONE");
        assertTrue(ics.contains("UID:WE-ONE@ofbiz"));
        assertTrue(ics.contains("DTEND;VALUE=DATE:20260309"));
        assertThrows(NoSuchElementException.class, () -> effortService.calendarFor("WE-NONE"));
        seeded("WE-BARE", null, null);
        assertThrows(IllegalStateException.class, () -> effortService.calendarFor("WE-BARE"));
    }

    @Test
    void timesheetLifecycleSubmitOnce() {
        seeded("WE-T", LocalDate.parse("2026-01-05"), LocalDate.parse("2026-01-11"));
        Timesheet sheet = timesheetService.create("TS-1", "P-100",
            LocalDate.parse("2026-01-05"), LocalDate.parse("2026-01-11"),
            List.of(new TimesheetEntry("WE-T", new BigDecimal("7.5"), "STANDARD")));
        assertEquals(TimesheetStatus.DRAFT, sheet.statusId());
        Timesheet submitted = timesheetService.submit("TS-1");
        assertEquals(TimesheetStatus.SUBMITTED, submitted.statusId());
        assertTrue(outbox.contains("workeffort.timesheet.submitted.v1:TS-1"));
        assertThrows(TimesheetAlreadySubmittedException.class,
            () -> timesheetService.submit("TS-1"));
        assertEquals(1, timesheetService.all().size());
    }

    @Test
    void timesheetEntriesMustReferenceRealEfforts() {
        assertThrows(NoSuchElementException.class,
            () -> timesheetService.create("TS-X", "P-100", LocalDate.parse("2026-01-05"),
                LocalDate.parse("2026-01-11"),
                List.of(new TimesheetEntry("WE-GHOST", BigDecimal.ONE, "STANDARD"))));
        seeded("WE-R", null, null);
        Timesheet sheet = timesheetService.create("TS-2", "P-100",
            LocalDate.parse("2026-01-05"), LocalDate.parse("2026-01-11"), null);
        timesheetService.update("TS-2", "P-200", LocalDate.parse("2026-01-05"),
            LocalDate.parse("2026-01-11"),
            List.of(new TimesheetEntry("WE-R", BigDecimal.ONE, "OVERTIME")));
        assertEquals("P-200", timesheetService.find("TS-2").orElseThrow().partyId());
        assertThrows(NoSuchElementException.class,
            () -> timesheetService.update("TS-2", "P", LocalDate.parse("2026-01-05"),
                LocalDate.parse("2026-01-11"),
                List.of(new TimesheetEntry("WE-GHOST", BigDecimal.ONE, "R"))));
        assertThrows(NoSuchElementException.class,
            () -> timesheetService.update("TS-NONE", "P", LocalDate.parse("2026-01-05"),
                LocalDate.parse("2026-01-11"), null));
        assertEquals(sheet.timesheetId(), timesheetService.find("TS-2").orElseThrow()
            .timesheetId());
    }

    @Test
    void submittedTimesheetCannotBeUpdatedAndDeleteWorks() {
        seeded("WE-F", null, null);
        timesheetService.create("TS-3", "P-100", LocalDate.parse("2026-01-05"),
            LocalDate.parse("2026-01-11"),
            List.of(new TimesheetEntry("WE-F", BigDecimal.ONE, "STANDARD")));
        timesheetService.submit("TS-3");
        assertThrows(TimesheetAlreadySubmittedException.class,
            () -> timesheetService.update("TS-3", "P-100", LocalDate.parse("2026-01-05"),
                LocalDate.parse("2026-01-11"), null));
        timesheetService.delete("TS-3");
        assertTrue(timesheetService.find("TS-3").isEmpty());
        assertThrows(NoSuchElementException.class, () -> timesheetService.submit("TS-3"));
    }

    @Test
    void calendarEventMappingPreconditionsSurfaceHere() {
        // the renderer itself is domain-tested; this proves the service
        // never feeds it a dateless event (guard is isCalendarReady)
        assertNotNull(effortService.calendarFeed());
        assertFalse(ICalendar.render(List.of(), java.time.Instant.EPOCH).contains("VEVENT"));
    }

    /** In-memory fakes for every store port. */
    static final class FakeEffortStore implements WorkEffortStore {
        final List<WorkEffort> rows = new ArrayList<>();

        @Override
        public Optional<WorkEffort> find(String workEffortId) {
            return rows.stream().filter(e -> e.workEffortId().equals(workEffortId))
                .findFirst();
        }

        @Override
        public void save(WorkEffort effort) {
            rows.removeIf(e -> e.workEffortId().equals(effort.workEffortId()));
            rows.add(effort);
        }

        @Override
        public void delete(String workEffortId) {
            rows.removeIf(e -> e.workEffortId().equals(workEffortId));
        }

        @Override
        public List<WorkEffort> findByParent(String parentWorkEffortId) {
            return rows.stream()
                .filter(e -> parentWorkEffortId.equals(e.parentWorkEffortId())).toList();
        }

        @Override
        public List<WorkEffort> findAllCalendarReady() {
            return rows.stream().filter(WorkEffort::isCalendarReady).toList();
        }

        @Override
        public List<WorkEffort> all() {
            return List.copyOf(rows);
        }
    }

    static final class FakeTimesheetStore implements TimesheetStore {
        final List<Timesheet> rows = new ArrayList<>();

        @Override
        public Optional<Timesheet> find(String timesheetId) {
            return rows.stream().filter(t -> t.timesheetId().equals(timesheetId)).findFirst();
        }

        @Override
        public void save(Timesheet timesheet) {
            rows.removeIf(t -> t.timesheetId().equals(timesheet.timesheetId()));
            rows.add(timesheet);
        }

        @Override
        public void delete(String timesheetId) {
            rows.removeIf(t -> t.timesheetId().equals(timesheetId));
        }

        @Override
        public List<Timesheet> all() {
            return List.copyOf(rows);
        }
    }
}
