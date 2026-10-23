package com.gradpilot.notify;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.gradpilot.checkin.CheckInDtos.CheckInResponse;
import com.gradpilot.push.PushMessage;
import com.gradpilot.today.TodayDtos.CountdownView;
import com.gradpilot.today.TodayDtos.ShiftView;
import com.gradpilot.today.TodayDtos.TodayResponse;
import com.gradpilot.today.TodayDtos.TrackedItem;
import com.gradpilot.tracker.Item;
import com.gradpilot.tracker.Tracker;
import com.gradpilot.tracker.TrackerType;
import com.gradpilot.tracker.dto.TrackerDtos.ItemResponse;
import com.gradpilot.user.User;

class NotificationRulesTest {

    private static final LocalDate DAY = LocalDate.of(2026, 10, 23);
    private static final LocalTime SEVEN_TEN = LocalTime.of(7, 10);

    @Test
    void digestIsDueOncePerDayWithinTwoHours() {
        assertThat(NotificationService.isDue(LocalTime.of(7, 9), SEVEN_TEN, null, DAY)).isFalse();
        assertThat(NotificationService.isDue(LocalTime.of(7, 10), SEVEN_TEN, null, DAY)).isTrue();
        assertThat(NotificationService.isDue(LocalTime.of(9, 9), SEVEN_TEN, DAY.minusDays(1), DAY)).isTrue();
        assertThat(NotificationService.isDue(LocalTime.of(9, 10), SEVEN_TEN, null, DAY)).isFalse();
        assertThat(NotificationService.isDue(LocalTime.of(7, 30), SEVEN_TEN, DAY, DAY)).isFalse();
    }

    private static ItemResponse shift(LocalDate day, String start, String end) {
        Item item = new Item(UUID.randomUUID(), "Kitchen shift");
        item.setItemDate(day);
        item.setStartTime(LocalTime.parse(start));
        item.setEndTime(LocalTime.parse(end));
        return ItemResponse.from(item);
    }

    private static CheckInResponse checkin(String focus, boolean done) {
        return new CheckInResponse(DAY, true, null, null, null, null, false, false, false, focus, done, null);
    }

    private static User user(String name) {
        User user = new User("n@example.com", "hash");
        user.setDisplayName(name);
        user.setBedTime(LocalTime.of(23, 0));
        return user;
    }

    @Test
    void morningMessageListsShiftDueItemsCountdownAndFocus() {
        TodayResponse today = new TodayResponse(DAY, SEVEN_TEN, "Australia/Melbourne",
                new ShiftView(UUID.randomUUID(), "Work shifts", shift(DAY, "16:00", "22:00"), LocalTime.of(15, 15), true),
                List.of(new TrackedItem(UUID.randomUUID(), "Licence", null),
                        new TrackedItem(UUID.randomUUID(), "Jobs", null)),
                List.of(new CountdownView(UUID.randomUUID(), "Dates", UUID.randomUUID(), "Parents arrive",
                        DAY.plusDays(50), 50)),
                checkin("Book the driving test", false),
                List.of());

        PushMessage message = DigestMessages.morning(user("Nithya"), today);

        assertThat(message.title()).isEqualTo("Good morning, Nithya");
        assertThat(message.body()).isEqualTo("Shift 4:00pm to 10:00pm, leave by 3:15pm · 2 things due today"
                + " · Parents arrive in 50 days · Today's one thing: Book the driving test");
    }

    @Test
    void morningMessageWorksOnAQuietDay() {
        TodayResponse today = new TodayResponse(DAY, SEVEN_TEN, "Australia/Melbourne", null, List.of(), List.of(),
                CheckInResponse.empty(DAY), List.of());

        PushMessage message = DigestMessages.morning(user(null), today);

        assertThat(message.title()).isEqualTo("Good morning");
        assertThat(message.body()).isEqualTo("No shift today · Pick today's one thing");
    }

    @Test
    void eveningMessageMentionsTomorrowsShiftAndBedtime() {
        TodayResponse today = new TodayResponse(DAY, LocalTime.of(21, 40), "Australia/Melbourne",
                new ShiftView(UUID.randomUUID(), "Work shifts", shift(DAY.plusDays(1), "09:00", "15:00"),
                        LocalTime.of(8, 15), false),
                List.of(), List.of(), checkin("Apply to Heidi", true), List.of());

        PushMessage message = DigestMessages.evening(user("Nithya"), today);

        assertThat(message.body()).isEqualTo(
                "You did today's one thing · Tomorrow: shift 9:00am, leave by 8:15am · Bed by 11:00pm");
    }

    @Test
    void shiftReminderIsUrgentAndOpensTheTracker() {
        Tracker shifts = new Tracker(UUID.randomUUID(), "Work shifts", TrackerType.SCHEDULE);
        ReflectionTestUtils.setField(shifts, "id", UUID.randomUUID());
        shifts.setSettings(Map.of(Tracker.SETTING_TRAVEL_MINUTES, 45));
        Item shift = new Item(shifts.getId(), "Kitchen shift");
        shift.setStartTime(LocalTime.of(16, 0));

        PushMessage message = NotificationService.reminderMessage(shifts, shift);

        assertThat(message.title()).isEqualTo("Time to leave");
        assertThat(message.body()).isEqualTo("Kitchen shift starts at 4:00pm");
        assertThat(message.url()).isEqualTo("/trackers/" + shifts.getId());
        assertThat(message.urgent()).isTrue();
    }

    @Test
    void countdownWording() {
        assertThat(DigestMessages.inDays(0)).isEqualTo("is today");
        assertThat(DigestMessages.inDays(1)).isEqualTo("is tomorrow");
        assertThat(DigestMessages.inDays(12)).isEqualTo("in 12 days");
    }
}
