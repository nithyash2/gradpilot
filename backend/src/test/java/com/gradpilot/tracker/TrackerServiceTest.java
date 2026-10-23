package com.gradpilot.tracker;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.gradpilot.tracker.dto.TrackerDtos.TrackerStats;

class TrackerServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 23);

    private static Item item(String title) {
        return new Item(UUID.randomUUID(), title);
    }

    @Test
    void leaveReminderSubtractsTravelTimeInUsersTimezone() {
        Tracker shifts = new Tracker(UUID.randomUUID(), "Work shifts", TrackerType.SCHEDULE);
        shifts.setSettings(Map.of(Tracker.SETTING_TRAVEL_MINUTES, 45));
        Item shift = item("Kitchen");
        shift.setItemDate(LocalDate.of(2026, 10, 24));
        shift.setStartTime(LocalTime.of(9, 0));

        Instant reminder = TrackerService.leaveReminder(shifts, shift, ZoneId.of("Australia/Melbourne"));

        // 8:15am Melbourne daylight time (UTC+11) is 21:15 UTC the day before
        assertThat(reminder).isEqualTo(Instant.parse("2026-10-23T21:15:00Z"));
    }

    @Test
    void pipelineStatsCountEveryStageInOrder() {
        Tracker jobs = new Tracker(UUID.randomUUID(), "Job hunt", TrackerType.PIPELINE);
        jobs.setSettings(Map.of(Tracker.SETTING_STAGES, TemplateCatalog.JOB_STAGES));
        Item a = item("A");
        a.setStage("Applied");
        Item b = item("B");
        b.setStage("Applied");
        Item c = item("C");
        c.setStage("Interview");

        TrackerStats stats = TrackerService.stats(jobs, List.of(a, b, c), TODAY);

        assertThat(stats.total()).isEqualTo(3);
        assertThat(stats.byStage()).containsEntry("Applied", 2).containsEntry("Interview", 1)
                .containsEntry("Offer", 0);
        assertThat(stats.byStage().keySet()).startsWith("To apply", "Applied");
    }

    @Test
    void progressStatsAverageOnlyItemsWithProgress() {
        Tracker study = new Tracker(UUID.randomUUID(), "Study", TrackerType.PROGRESS);
        Item a = item("Python");
        a.setProgress(40);
        Item b = item("PyTorch");
        b.setProgress(21);
        Item c = item("Not started");

        TrackerStats stats = TrackerService.stats(study, List.of(a, b, c), TODAY);

        assertThat(stats.averageProgress()).isEqualTo(31);
    }

    @Test
    void logStatsSumAmounts() {
        Tracker log = new Tracker(UUID.randomUUID(), "Lessons", TrackerType.LOG);
        Item a = item("Lesson");
        a.setAmount(new BigDecimal("60"));
        Item b = item("Lesson");
        b.setAmount(new BigDecimal("45.5"));

        assertThat(TrackerService.stats(log, List.of(a, b), TODAY).totalAmount())
                .isEqualByComparingTo("105.5");
    }

    @Test
    void nextDateIgnoresPastDates() {
        Tracker countdown = new Tracker(UUID.randomUUID(), "Dates", TrackerType.COUNTDOWN);
        Item past = item("Past");
        past.setItemDate(TODAY.minusDays(1));
        Item soon = item("Parents arrive");
        soon.setItemDate(LocalDate.of(2026, 12, 12));
        Item later = item("Graduation");
        later.setItemDate(LocalDate.of(2026, 12, 17));

        assertThat(TrackerService.stats(countdown, List.of(past, later, soon), TODAY).nextDate())
                .isEqualTo(LocalDate.of(2026, 12, 12));
    }

    @Test
    void changingReminderRearmsIt() {
        Item item = item("Follow up");
        item.setReminderAt(Instant.parse("2026-10-24T00:00:00Z"));
        item.markReminderSent(Instant.parse("2026-10-24T00:00:05Z"));

        item.setReminderAt(Instant.parse("2026-10-24T00:00:00Z"));
        assertThat(item.getReminderSentAt()).isNotNull();

        item.setReminderAt(Instant.parse("2026-10-25T00:00:00Z"));
        assertThat(item.getReminderSentAt()).isNull();
    }
}
