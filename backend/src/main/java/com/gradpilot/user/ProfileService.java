package com.gradpilot.user;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.gradpilot.auth.dto.UserResponse;

@Service
public class ProfileService {

    private final UserRepository users;

    public ProfileService(UserRepository users) {
        this.users = users;
    }

    @Transactional(readOnly = true)
    public UserResponse get(UUID userId) {
        return UserResponse.from(load(userId));
    }

    @Transactional
    public UserResponse update(UUID userId, UpdateProfileRequest request) {
        User user = load(userId);
        if (request.displayName() != null) {
            user.setDisplayName(request.displayName().isBlank() ? null : request.displayName().trim());
        }
        if (request.timezone() != null) {
            user.setTimezone(Timezones.valid(request.timezone()));
        }
        if (request.wakeTime() != null) {
            user.setWakeTime(request.wakeTime());
        }
        if (request.bedTime() != null) {
            user.setBedTime(request.bedTime());
        }
        if (request.morningTime() != null) {
            user.setMorningTime(request.morningTime());
        }
        if (request.eveningTime() != null) {
            user.setEveningTime(request.eveningTime());
        }
        return UserResponse.from(user);
    }

    private User load(UUID userId) {
        // A valid token for an account that has since been deleted
        return users.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    }
}
