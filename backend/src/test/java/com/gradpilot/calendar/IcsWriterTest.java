package com.gradpilot.calendar;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

class IcsWriterTest {

    private static final Instant STAMP = Instant.parse("2026-10-23T06:00:00Z");

    @Test
    void writesTimedAndAllDayEventsWithCrlfLineEndings() {
        String ics = IcsWriter.write("GradPilot", List.of(
                IcsWriter.Event.timed("shift-1@gradpilot", "Shift", "Work shifts", STAMP,
                        Instant.parse("2026-10-24T21:00:00Z"), Instant.parse("2026-10-25T03:00:00Z"),
                        45, "Time to leave for Shift"),
                IcsWriter.Event.allDay("date-1@gradpilot", "Parents arrive", null, STAMP,
                        LocalDate.of(2026, 12, 12))));

        assertThat(ics).startsWith("BEGIN:VCALENDAR\r\nVERSION:2.0\r\n").endsWith("END:VCALENDAR\r\n");
        assertThat(ics.replace("\r\n", "")).doesNotContain("\n");
        assertThat(ics).contains("DTSTART:20261024T210000Z\r\n", "DTEND:20261025T030000Z\r\n",
                "TRIGGER:-PT45M\r\n", "DESCRIPTION:Time to leave for Shift\r\n",
                "DTSTART;VALUE=DATE:20261212\r\n", "DTEND;VALUE=DATE:20261213\r\n",
                "UID:shift-1@gradpilot\r\n", "DTSTAMP:20261023T060000Z\r\n");
        assertThat(ics.split("BEGIN:VEVENT", -1)).hasSize(3);
    }

    @Test
    void escapesSpecialCharacters() {
        assertThat(IcsWriter.escape("Canva, Sydney; \"grad\" role\nnext line \\ end"))
                .isEqualTo("Canva\\, Sydney\\; \"grad\" role\\nnext line \\\\ end");
    }

    @Test
    void foldsLongLinesAtSeventyFiveBytesWithoutSplittingCharacters() {
        StringBuilder out = new StringBuilder();
        String summary = "SUMMARY:" + "Interview at Canva – கனவு ".repeat(8);
        IcsWriter.line(out, summary);

        String[] physical = out.toString().split("\r\n");
        for (String line : physical) {
            assertThat(line.getBytes(StandardCharsets.UTF_8).length).isLessThanOrEqualTo(75);
        }
        for (int i = 1; i < physical.length; i++) {
            assertThat(physical[i]).startsWith(" ");
        }
        String unfolded = out.toString().replace("\r\n ", "").replace("\r\n", "");
        assertThat(unfolded).isEqualTo(summary);
    }
}
