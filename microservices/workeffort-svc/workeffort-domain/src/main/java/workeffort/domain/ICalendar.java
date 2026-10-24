package workeffort.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * P6-T2 iCalendar (RFC 5545) renderer — pure and unit-testable, this is
 * what the CG proves ("feed renders in an external calendar"). Lines are
 * CRLF-terminated; DATE values carry VALUE=DATE; DTEND is exclusive per
 * RFC 5545 §3.6.1, so an inclusive completion date ships as +1 day.
 * Folding is not needed: effort names are short.
 */
public final class ICalendar {

    private static final DateTimeFormatter DATE_STAMP = DateTimeFormatter
        .ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter DATE_VALUE = DateTimeFormatter
        .ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC);

    /** One VEVENT's worth of data; endDateInclusive is the effort estimate. */
    public record CalendarEvent(String uid, String summary, LocalDate startDate,
            LocalDate endDateInclusive) {

        public CalendarEvent {
            if (uid == null || uid.isBlank()) {
                throw new IllegalArgumentException("uid must not be blank");
            }
            if (summary == null) {
                throw new IllegalArgumentException("summary must not be null");
            }
            if (startDate == null || endDateInclusive == null
                    || endDateInclusive.isBefore(startDate)) {
                throw new IllegalArgumentException("endDate must not be before startDate");
            }
        }
    }

    public static String render(List<CalendarEvent> events, Instant generatedAt) {
        StringBuilder ics = new StringBuilder();
        line(ics, "BEGIN:VCALENDAR");
        line(ics, "VERSION:2.0");
        line(ics, "PRODID:-//OFBiz//workeffort-svc//EN");
        line(ics, "CALSCALE:GREGORIAN");
        String stamp = DATE_STAMP.format(generatedAt);
        for (CalendarEvent event : events) {
            line(ics, "BEGIN:VEVENT");
            line(ics, "UID:" + event.uid() + "@ofbiz");
            line(ics, "DTSTAMP:" + stamp);
            line(ics, "DTSTART;VALUE=DATE:" + DATE_VALUE.format(event.startDate()));
            line(ics, "DTEND;VALUE=DATE:" + DATE_VALUE.format(event.endDateInclusive().plusDays(1)));
            line(ics, "SUMMARY:" + escape(event.summary()));
            line(ics, "END:VEVENT");
        }
        line(ics, "END:VCALENDAR");
        return ics.toString();
    }

    /** RFC 5545 §3.3.11: backslash first, then semicolon, comma, newlines. */
    static String escape(String text) {
        return text.replace("\\", "\\\\")
            .replace(";", "\\;")
            .replace(",", "\\,")
            .replace("\r\n", "\\n")
            .replace("\n", "\\n");
    }

    private static void line(StringBuilder ics, String content) {
        ics.append(content).append("\r\n");
    }

    private ICalendar() {
    }
}
