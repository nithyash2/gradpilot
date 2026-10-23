package com.gradpilot.checkin;

import java.time.LocalDate;
import java.time.LocalTime;

import jakarta.validation.constraints.Size;

public final class CheckInDtos {

    private CheckInDtos() {
    }

    public record CheckInRequest(
            LocalTime wakeTime,
            @Size(max = 200) String breakfast,
            @Size(max = 200) String lunch,
            @Size(max = 200) String dinner,
            Boolean selfCare,
            Boolean dishes,
            Boolean bedOnTime,
            @Size(max = 200) String focus,
            Boolean focusDone,
            @Size(max = 5000) String notes) {
    }

    /** {@code saved} is false for a day with no check-in yet. */
    public record CheckInResponse(
            LocalDate day,
            boolean saved,
            LocalTime wakeTime,
            String breakfast,
            String lunch,
            String dinner,
            boolean selfCare,
            boolean dishes,
            boolean bedOnTime,
            String focus,
            boolean focusDone,
            String notes) {

        public static CheckInResponse from(CheckIn c) {
            return new CheckInResponse(c.getDay(), true, c.getWakeTime(), c.getBreakfast(), c.getLunch(),
                    c.getDinner(), c.isSelfCare(), c.isDishes(), c.isBedOnTime(), c.getFocus(),
                    c.isFocusDone(), c.getNotes());
        }

        public static CheckInResponse empty(LocalDate day) {
            return new CheckInResponse(day, false, null, null, null, null, false, false, false,
                    null, false, null);
        }
    }
}
