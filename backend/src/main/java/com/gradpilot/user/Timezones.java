package com.gradpilot.user;

import java.time.DateTimeException;
import java.time.ZoneId;

/** Checks timezone names such as "Australia/Melbourne". */
public final class Timezones {

    private Timezones() {
    }

    /** Returns the canonical id, or throws IllegalArgumentException for an unknown zone. */
    public static String valid(String timezone) {
        try {
            return ZoneId.of(timezone.trim()).getId();
        } catch (DateTimeException | NullPointerException e) {
            throw new IllegalArgumentException("Unknown timezone: " + timezone);
        }
    }
}
