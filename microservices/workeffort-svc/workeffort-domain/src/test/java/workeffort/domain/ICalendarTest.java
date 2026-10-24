package workeffort.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * P6-T2 CG proof, in the domain: the rendered feed must be a valid RFC
 * 5545 body an external calendar accepts — CRLF everywhere, DATE values
 * typed, text escaped, DTEND exclusive.
 */
class ICalendarTest {

    private static final Instant GENERATED_AT = Instant.parse("2026-10-23T12:00:00Z");

    @Test
    void rendersHeaderEventAndFooterWithCrlfLineEndings() {
        String ics = ICalendar.render(List.of(new ICalendar.CalendarEvent("WE-1",
            "Sprint 6 kickoff", LocalDate.parse("2026-01-06"),
            LocalDate.parse("2026-01-06"))), GENERATED_AT);
        String[] lines = ics.split("\r\n", -1);
        assertEquals("BEGIN:VCALENDAR", lines[0]);
        assertEquals("VERSION:2.0", lines[1]);
        assertEquals("PRODID:-//OFBiz//workeffort-svc//EN", lines[2]);
        assertEquals("CALSCALE:GREGORIAN", lines[3]);
        assertEquals("BEGIN:VEVENT", lines[4]);
        assertEquals("UID:WE-1@ofbiz", lines[5]);
        assertEquals("DTSTAMP:20261023T120000Z", lines[6]);
        assertEquals("DTSTART;VALUE=DATE:20260106", lines[7]);
        assertEquals("DTEND;VALUE=DATE:20260107", lines[8]);
        assertEquals("SUMMARY:Sprint 6 kickoff", lines[9]);
        assertEquals("END:VEVENT", lines[10]);
        assertEquals("END:VCALENDAR", lines[11]);
        assertEquals("", lines[12], "CRLF-terminated body ends with a final CRLF");
        assertEquals(13, lines.length);
    }

    @Test
    void multiDayEventSpansThroughTheInclusiveCompletionDate() {
        String ics = ICalendar.render(List.of(new ICalendar.CalendarEvent("WE-2", "build",
            LocalDate.parse("2026-01-05"), LocalDate.parse("2026-01-09"))), GENERATED_AT);
        // DTEND is exclusive (RFC 5545 3.6.1): the 9th renders as a covered day
        assertTrue(ics.contains("DTEND;VALUE=DATE:20260110\r\n"));
    }

    @Test
    void multipleEventsEachBecomeAVevent() {
        String ics = ICalendar.render(List.of(
            new ICalendar.CalendarEvent("A", "first", LocalDate.parse("2026-01-05"),
                LocalDate.parse("2026-01-06")),
            new ICalendar.CalendarEvent("B", "second", LocalDate.parse("2026-02-01"),
                LocalDate.parse("2026-02-02"))), GENERATED_AT);
        assertEquals(2, ics.split("BEGIN:VEVENT", -1).length - 1);
        assertTrue(ics.contains("UID:A@ofbiz"));
        assertTrue(ics.contains("UID:B@ofbiz"));
    }

    @Test
    void emptyFeedIsStillAValidCalendar() {
        String ics = ICalendar.render(List.of(), GENERATED_AT);
        assertTrue(ics.startsWith("BEGIN:VCALENDAR\r\n"));
        assertTrue(ics.endsWith("END:VCALENDAR\r\n"));
        assertFalse(ics.contains("VEVENT"));
    }

    @Test
    void escapesRfc5545SpecialCharacters() {
        assertEquals("plain", ICalendar.escape("plain"));
        assertEquals("a\\,b", ICalendar.escape("a,b"));
        assertEquals("a\\;b", ICalendar.escape("a;b"));
        assertEquals("a\\\\b", ICalendar.escape("a\\b"));
        assertEquals("line\\nbreak", ICalendar.escape("line\nbreak"));
        assertEquals("line\\nbreak", ICalendar.escape("line\r\nbreak"));
        String ics = ICalendar.render(List.of(new ICalendar.CalendarEvent("WE-3",
            "Road, Curve; Bench\\cut", LocalDate.parse("2026-01-05"),
            LocalDate.parse("2026-01-06"))), GENERATED_AT);
        assertTrue(ics.contains("SUMMARY:Road\\, Curve\\; Bench\\\\cut\r\n"));
    }

    @Test
    void eventGuards() {
        assertThrows(IllegalArgumentException.class,
            () -> new ICalendar.CalendarEvent(" ", "s", LocalDate.parse("2026-01-05"),
                LocalDate.parse("2026-01-06")));
        assertThrows(IllegalArgumentException.class,
            () -> new ICalendar.CalendarEvent("U", null, LocalDate.parse("2026-01-05"),
                LocalDate.parse("2026-01-06")));
        assertThrows(IllegalArgumentException.class,
            () -> new ICalendar.CalendarEvent("U", "s", null, LocalDate.parse("2026-01-06")));
        assertThrows(IllegalArgumentException.class,
            () -> new ICalendar.CalendarEvent("U", "s", LocalDate.parse("2026-01-05"), null));
        assertThrows(IllegalArgumentException.class,
            () -> new ICalendar.CalendarEvent("U", "s", LocalDate.parse("2026-01-06"),
                LocalDate.parse("2026-01-05")));
    }
}
