package com.gradpilot.auth;

import java.time.DateTimeException;
import java.time.ZoneId;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gradpilot.auth.dto.AuthResponse;
import com.gradpilot.auth.dto.LoginRequest;
import com.gradpilot.auth.dto.SignupRequest;
import com.gradpilot.auth.dto.UserResponse;
import com.gradpilot.user.User;
import com.gradpilot.user.UserRepository;

@Service
public class AuthService {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokens;

    public AuthService(UserRepository users, PasswordEncoder passwordEncoder, TokenService tokens) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.tokens = tokens;
    }

    @Transactional
    public AuthResponse signup(SignupRequest request) {
        if (users.existsByEmailIgnoreCase(request.email().trim())) {
            throw new EmailAlreadyUsedException();
        }

        User user = new User(request.email(), passwordEncoder.encode(request.password()));
        user.setDisplayName(blankToNull(request.displayName()));
        if (request.timezone() != null && !request.timezone().isBlank()) {
            user.setTimezone(validTimezone(request.timezone().trim()));
        }

        try {
            user = users.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            // Two sign-ups with the same email at the same moment
            throw new EmailAlreadyUsedException();
        }
        return respond(user);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        User user = users.findByEmailIgnoreCase(request.email().trim())
                .orElseThrow(InvalidCredentialsException::new);
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }
        return respond(user);
    }

    private AuthResponse respond(User user) {
        TokenService.IssuedToken token = tokens.issue(user);
        return new AuthResponse(token.value(), token.expiresAt(), UserResponse.from(user));
    }

    private static String validTimezone(String timezone) {
        try {
            return ZoneId.of(timezone).getId();
        } catch (DateTimeException e) {
            throw new IllegalArgumentException("Unknown timezone: " + timezone);
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
