package com.gradpilot.user;

import java.time.LocalTime;

import jakarta.validation.constraints.Size;

/** Body of {@code PUT /api/me}. Fields left out keep their current value. */
public record UpdateProfileRequest(
        @Size(max = 100) String displayName,
        @Size(max = 64) String timezone,
        LocalTime wakeTime,
        LocalTime bedTime,
        LocalTime morningTime,
        LocalTime eveningTime) {
}
