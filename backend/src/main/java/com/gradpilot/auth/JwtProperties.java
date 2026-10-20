package com.gradpilot.auth;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings for signing login tokens, read from {@code gradpilot.jwt.*}.
 *
 * @param secret key used to sign tokens; at least 32 bytes for HS256
 * @param ttl    how long a token stays valid
 */
@ConfigurationProperties(prefix = "gradpilot.jwt")
public record JwtProperties(String secret, Duration ttl) {

    public static final int MIN_SECRET_BYTES = 32;

    public JwtProperties {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalArgumentException(
                    "gradpilot.jwt.secret must be at least " + MIN_SECRET_BYTES + " bytes");
        }
        if (ttl == null || ttl.isNegative() || ttl.isZero()) {
            throw new IllegalArgumentException("gradpilot.jwt.ttl must be a positive duration");
        }
    }
}
