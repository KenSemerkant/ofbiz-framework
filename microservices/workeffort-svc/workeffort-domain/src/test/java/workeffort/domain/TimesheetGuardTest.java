package workeffort.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * P6-T2 guard tests: submission is one-way and a submitted timesheet is
 * frozen — payroll consumes submitted sheets, so edits after submission
 * would corrupt what was approved.
 */
class TimesheetGuardTest {

    private static final LocalDate FROM = LocalDate.parse("2026-01-05");
    private static final LocalDate THRU = LocalDate.parse("2026-01-11");

    private static Timesheet sheet() {
        return new Timesheet("TS-1", "P-100", FROM, THRU,
            List.of(new TimesheetEntry("WE-1", new BigDecimal("7.5"), "STANDARD")));
    }

    @Test
    void submitIsOneWay() {
        Timesheet timesheet = sheet();
        assertEquals(TimesheetStatus.DRAFT, timesheet.statusId());
        timesheet.submit();
        assertEquals(TimesheetStatus.SUBMITTED, timesheet.statusId());
        assertThrows(TimesheetAlreadySubmittedException.class, timesheet::submit);
    }

    @Test
    void submittedSheetsAreFrozen() {
        Timesheet timesheet = sheet();
        timesheet.submit();
        assertThrows(TimesheetAlreadySubmittedException.class,
            () -> timesheet.updateDetails("P-200", FROM, THRU, List.of()));
        assertEquals("P-100", timesheet.partyId());
    }

    @Test
    void draftUpdatesApplyAndRevalidate() {
        Timesheet timesheet = sheet();
        timesheet.updateDetails("P-200", FROM, THRU, List.of(
            new TimesheetEntry("WE-2", BigDecimal.ONE, "OVERTIME")));
        assertEquals("P-200", timesheet.partyId());
        assertEquals(1, timesheet.entries().size());
        assertThrows(IllegalArgumentException.class,
            () -> timesheet.updateDetails(" ", FROM, THRU, List.of()));
        assertThrows(IllegalArgumentException.class,
            () -> timesheet.updateDetails("P", THRU, FROM, List.of()), "thru before from");
    }

    @Test
    void constructionGuards() {
        assertThrows(IllegalArgumentException.class,
            () -> new Timesheet(" ", "P", FROM, THRU, List.of()));
        assertThrows(IllegalArgumentException.class,
            () -> new Timesheet("TS", null, FROM, THRU, List.of()));
        assertThrows(IllegalArgumentException.class,
            () -> new Timesheet("TS", "P", THRU, FROM, List.of()));
        assertThrows(IllegalArgumentException.class,
            () -> new Timesheet("TS", "P", FROM, null, List.of()));
        assertThrows(IllegalArgumentException.class,
            () -> new TimesheetEntry(" ", BigDecimal.ONE, "R"));
        assertThrows(IllegalArgumentException.class,
            () -> new TimesheetEntry("WE", BigDecimal.ZERO, "R"));
        assertThrows(IllegalArgumentException.class,
            () -> new TimesheetEntry("WE", new BigDecimal("-1"), "R"));
        assertThrows(IllegalArgumentException.class,
            () -> new TimesheetEntry("WE", BigDecimal.ONE, " "));
    }

    @Test
    void nullEntriesBecomeEmptyAndAccessorsHold() {
        Timesheet timesheet = new Timesheet("TS-2", "P-100", FROM, THRU, null);
        assertEquals(0, timesheet.entries().size());
        assertEquals("TS-2", timesheet.timesheetId());
        assertEquals(FROM, timesheet.fromDate());
        assertEquals(THRU, timesheet.thruDate());
        Timesheet rehydrated = Timesheet.rehydrate("TS-R", "P-100", FROM, THRU, List.of(
            new TimesheetEntry("WE-1", BigDecimal.ONE, "STANDARD")),
            TimesheetStatus.SUBMITTED);
        assertEquals(TimesheetStatus.SUBMITTED, rehydrated.statusId());
        assertEquals(1, rehydrated.entries().size());
    }
}
