package com.gradpilot.notify;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.gradpilot.push.PushMessage;
import com.gradpilot.today.TodayDtos.CountdownView;
import com.gradpilot.today.TodayDtos.ShiftView;
import com.gradpilot.today.TodayDtos.TodayResponse;
import com.gradpilot.user.User;

/** Writes the words of the morning plan and the evening wind-down. */
public final class DigestMessages {

    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("h:mma", Locale.ENGLISH);

    private DigestMessages() {
    }

    public static PushMessage morning(User user, TodayResponse today) {
        List<String> lines = new ArrayList<>();

        ShiftView shift = today.nextShift();
        if (shift != null && shift.today()) {
            lines.add("Shift " + shiftTimes(shift) + leaveBy(shift));
        } else {
            lines.add("No shift today");
        }

        int due = today.dueToday().size();
        if (due > 0) {
            lines.add(due + (due == 1 ? " thing" : " things") + " due today");
        }

        if (!today.countdowns().isEmpty()) {
            CountdownView next = today.countdowns().get(0);
            lines.add(next.title() + " " + inDays(next.daysLeft()));
        }

        String focus = today.checkin().focus();
        lines.add(focus == null ? "Pick today's one thing" : "Today's one thing: " + focus);

        String name = user.getDisplayName();
        String title = name == null ? "Good morning" : "Good morning, " + name;
        return PushMessage.of(title, String.join(" · ", lines), "/", "morning");
    }

    public static PushMessage evening(User user, TodayResponse today) {
        List<String> lines = new ArrayList<>();

        if (today.checkin().focusDone()) {
            lines.add("You did today's one thing");
        } else if (today.checkin().focus() != null) {
            lines.add("Today's one thing is still open, and that's okay");
        } else {
            lines.add("Pick tomorrow's one thing");
        }

        ShiftView shift = today.nextShift();
        if (shift != null && shift.shift().itemDate().equals(today.date().plusDays(1))) {
            lines.add("Tomorrow: shift " + time(shift.shift().startTime()) + leaveBy(shift));
        }

        if (user.getBedTime() != null) {
            lines.add("Bed by " + time(user.getBedTime()));
        }
        return PushMessage.of("Time to wind down", String.join(" · ", lines), "/", "evening");
    }

    static String time(LocalTime time) {
        return time == null ? "" : CLOCK.format(time).toLowerCase(Locale.ENGLISH);
    }

    private static String shiftTimes(ShiftView shift) {
        String start = time(shift.shift().startTime());
        String end = time(shift.shift().endTime());
        return end.isEmpty() ? start : start + " to " + end;
    }

    private static String leaveBy(ShiftView shift) {
        return shift.leaveAt() == null ? "" : ", leave by " + time(shift.leaveAt());
    }

    static String inDays(long days) {
        if (days <= 0) {
            return "is today";
        }
        if (days == 1) {
            return "is tomorrow";
        }
        return "in " + days + " days";
    }
}
