package com.gradpilot.auth.dto;

import jakarta.validation.constraints.NotBlank;

/** Body of {@code POST /api/auth/login}. */
public record LoginRequest(@NotBlank String email, @NotBlank String password) {
}
