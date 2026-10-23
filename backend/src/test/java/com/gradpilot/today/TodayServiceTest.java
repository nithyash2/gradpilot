package com.gradpilot.today;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.gradpilot.today.TodayDtos.ShiftView;
import com.gradpilot.tracker.Item;
import com.gradpilot.tracker.Tracker;
import com.gradpilot.tracker.TrackerType;

class TodayServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 23);

    private final Tracker shifts = tracker();

    private static Tracker tracker() {
        Tracker t = new Tracker(UUID.randomUUID(), "Work shifts", TrackerType.SCHEDULE);
        ReflectionTestUtils.setField(t, "id", UUID.randomUUID());
        t.setSettings(Map.of(Tracker.SETTING_TRAVEL_MINUTES, 45));
        return t;
    }

    private Item shift(String title, LocalDate day, String start, String end) {
        Item item = new Item(shifts.getId(), title);
        item.setItemDate(day);
        item.setStartTime(LocalTime.parse(start));
        item.setEndTime(end == null ? null : LocalTime.parse(end));
        return item;
    }

    private ShiftView next(LocalTime now, Item... list) {
        return TodayService.nextShift(List.of(list), Map.of(shifts.getId(), shifts), TODAY, now);
    }

    @Test
    void skipsShiftThatFinishedEarlierToday() {
        Item morning = shift("Morning", TODAY, "06:00", "10:00");
        Item tomorrow = shift("Tomorrow", TODAY.plusDays(1), "09:00", "15:00");

        ShiftView view = next(LocalTime.of(12, 0), morning, tomorrow);

        assertThat(view.shift().title()).isEqualTo("Tomorrow");
        assertThat(view.today()).isFalse();
        assertThat(view.leaveAt()).isEqualTo(LocalTime.of(8, 15));
    }

    @Test
    void keepsShiftThatIsStillRunning() {
        Item running = shift("Lunch", TODAY, "11:00", "15:00");

        ShiftView view = next(LocalTime.of(12, 0), running);

        assertThat(view.shift().title()).isEqualTo("Lunch");
        assertThat(view.today()).isTrue();
    }

    @Test
    void keepsOvernightShift() {
        Item night = shift("Night", TODAY, "22:00", "02:00");

        assertThat(next(LocalTime.of(23, 30), night)).isNotNull();
    }

    @Test
    void shiftWithoutEndTimeFinishesWhenItStarts() {
        Item noEnd = shift("Open", TODAY, "09:00", null);

        assertThat(next(LocalTime.of(10, 0), noEnd)).isNull();
        assertThat(next(LocalTime.of(8, 0), noEnd)).isNotNull();
    }

    @Test
    void returnsNullWithNoShifts() {
        assertThat(next(LocalTime.NOON)).isNull();
    }
}
