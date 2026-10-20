package com.gradpilot.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.util.ReflectionTestUtils;

import com.gradpilot.config.SecurityConfig;
import com.gradpilot.user.User;

class TokenServiceTest {

    private static final String SECRET = "test-secret-that-is-at-least-thirty-two-bytes-long";

    @Test
    void issuedTokenCarriesUserIdEmailAndExpiry() {
        JwtProperties properties = new JwtProperties(SECRET, Duration.ofDays(7));
        SecurityConfig config = new SecurityConfig();
        Instant now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        TokenService tokens = new TokenService(
                config.jwtEncoder(properties), properties, Clock.fixed(now, ZoneOffset.UTC));

        User user = new User("nithya@example.com", "hash");
        UUID id = UUID.randomUUID();
        ReflectionTestUtils.setField(user, "id", id);

        TokenService.IssuedToken issued = tokens.issue(user);
        Jwt decoded = config.jwtDecoder(properties).decode(issued.value());

        assertThat(decoded.getSubject()).isEqualTo(id.toString());
        assertThat(decoded.getClaimAsString("email")).isEqualTo("nithya@example.com");
        assertThat(decoded.getClaimAsString("iss")).isEqualTo("gradpilot");
        assertThat(issued.expiresAt()).isEqualTo(now.plus(Duration.ofDays(7)));
    }

    @Test
    void rejectsShortSecret() {
        assertThatThrownBy(() -> new JwtProperties("too-short", Duration.ofDays(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNonPositiveTtl() {
        assertThatThrownBy(() -> new JwtProperties(SECRET, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
