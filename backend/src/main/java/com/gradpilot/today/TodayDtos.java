package com.gradpilot.today;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import com.gradpilot.checkin.CheckInDtos.CheckInResponse;
import com.gradpilot.tracker.dto.TrackerDtos.ItemResponse;

public final class TodayDtos {

    private TodayDtos() {
    }

    /** Everything the Today screen shows, in the user's own timezone. */
    public record TodayResponse(
            LocalDate date,
            LocalTime now,
            String timezone,
            ShiftView nextShift,
            List<TrackedItem> dueToday,
            List<CountdownView> countdowns,
            CheckInResponse checkin,
            List<DayMark> lastSevenDays) {
    }

    /** The next shift that has not finished yet, with the time to leave home. */
    public record ShiftView(
            UUID trackerId,
            String trackerName,
            ItemResponse shift,
            LocalTime leaveAt,
            boolean today) {
    }

    public record TrackedItem(UUID trackerId, String trackerName, ItemResponse item) {
    }

    public record CountdownView(
            UUID trackerId,
            String trackerName,
            UUID itemId,
            String title,
            LocalDate date,
            long daysLeft) {
    }

    /** Whether "today's one thing" was done on a past day. */
    public record DayMark(LocalDate date, boolean focusDone) {
    }
}
