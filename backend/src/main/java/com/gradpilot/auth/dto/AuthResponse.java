package com.gradpilot.auth.dto;

import java.time.Instant;

/** Returned after a successful sign-up or log-in. */
public record AuthResponse(String token, Instant expiresAt, UserResponse user) {
}
