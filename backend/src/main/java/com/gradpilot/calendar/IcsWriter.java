package com.gradpilot.calendar;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Writes an iCalendar (RFC 5545) feed: CRLF line endings, long lines folded
 * at 75 bytes, and text escaped, so Google, Apple and Outlook Calendar all
 * read it.
 */
public final class IcsWriter {

    private static final DateTimeFormatter UTC = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
            .withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter DATE = DateTimeFormatter.BASIC_ISO_DATE;
    private static final int MAX_LINE_BYTES = 75;

    /** One calendar entry. All-day entries have a date; timed entries have start and end. */
    public record Event(
            String uid,
            String summary,
            String description,
            Instant stamp,
            LocalDate day,
            Instant start,
            Instant end,
            Integer alarmMinutesBefore,
            String alarmText) {

        public static Event allDay(String uid, String summary, String description, Instant stamp, LocalDate day) {
            return new Event(uid, summary, description, stamp, day, null, null, null, null);
        }

        public static Event timed(String uid, String summary, String description, Instant stamp,
                                  Instant start, Instant end, Integer alarmMinutesBefore, String alarmText) {
            return new Event(uid, summary, description, stamp, null, start, end, alarmMinutesBefore, alarmText);
        }
    }

    private IcsWriter() {
    }

    public static String write(String calendarName, List<Event> events) {
        StringBuilder out = new StringBuilder();
        line(out, "BEGIN:VCALENDAR");
        line(out, "VERSION:2.0");
        line(out, "PRODID:-//GradPilot//Calendar feed//EN");
        line(out, "CALSCALE:GREGORIAN");
        line(out, "METHOD:PUBLISH");
        line(out, "X-WR-CALNAME:" + escape(calendarName));
        line(out, "REFRESH-INTERVAL;VALUE=DURATION:PT1H");
        line(out, "X-PUBLISHED-TTL:PT1H");
        for (Event event : events) {
            line(out, "BEGIN:VEVENT");
            line(out, "UID:" + escape(event.uid()));
            line(out, "DTSTAMP:" + UTC.format(event.stamp()));
            if (event.day() != null) {
                line(out, "DTSTART;VALUE=DATE:" + DATE.format(event.day()));
                line(out, "DTEND;VALUE=DATE:" + DATE.format(event.day().plusDays(1)));
                line(out, "TRANSP:TRANSPARENT");
            } else {
                line(out, "DTSTART:" + UTC.format(event.start()));
                line(out, "DTEND:" + UTC.format(event.end()));
            }
            line(out, "SUMMARY:" + escape(event.summary()));
            if (event.description() != null && !event.description().isBlank()) {
                line(out, "DESCRIPTION:" + escape(event.description()));
            }
            if (event.alarmMinutesBefore() != null && event.alarmMinutesBefore() > 0) {
                line(out, "BEGIN:VALARM");
                line(out, "ACTION:DISPLAY");
                line(out, "DESCRIPTION:" + escape(event.alarmText() != null ? event.alarmText() : event.summary()));
                line(out, "TRIGGER:-PT" + event.alarmMinutesBefore() + "M");
                line(out, "END:VALARM");
            }
            line(out, "END:VEVENT");
        }
        line(out, "END:VCALENDAR");
        return out.toString();
    }

    /** Escapes text values as RFC 5545 section 3.3.11 asks. */
    static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\")
                .replace(";", "\\;")
                .replace(",", "\\,")
                .replace("\r\n", "\\n")
                .replace("\n", "\\n")
                .replace("\r", "\\n");
    }

    /** Adds a content line, folding it so no physical line is longer than 75 bytes. */
    static void line(StringBuilder out, String content) {
        int bytes = 0;
        boolean first = true;
        StringBuilder current = new StringBuilder();
        for (int i = 0; i < content.length(); ) {
            int codePoint = content.codePointAt(i);
            String ch = new String(Character.toChars(codePoint));
            int size = ch.getBytes(StandardCharsets.UTF_8).length;
            int limit = first ? MAX_LINE_BYTES : MAX_LINE_BYTES - 1;
            if (bytes + size > limit) {
                out.append(current).append("\r\n ");
                current.setLength(0);
                bytes = 0;
                first = false;
            }
            current.append(ch);
            bytes += size;
            i += Character.charCount(codePoint);
        }
        out.append(current).append("\r\n");
    }
}
