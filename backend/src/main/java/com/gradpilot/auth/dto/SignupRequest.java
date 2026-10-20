package com.gradpilot.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST /api/auth/signup}.
 * BCrypt only reads the first 72 bytes of a password, hence the upper limit.
 */
public record SignupRequest(
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(min = 8, max = 72) String password,
        @Size(max = 100) String displayName,
        @Size(max = 64) String timezone) {
}
