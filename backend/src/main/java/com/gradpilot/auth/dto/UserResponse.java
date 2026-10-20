package com.gradpilot.auth.dto;

import java.time.LocalTime;
import java.util.UUID;

import com.gradpilot.user.User;

/** The public view of a user. Never includes the password hash. */
public record UserResponse(
        UUID id,
        String email,
        String displayName,
        String timezone,
        LocalTime wakeTime,
        LocalTime bedTime) {

    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getDisplayName(),
                user.getTimezone(),
                user.getWakeTime(),
                user.getBedTime());
    }
}
