package com.gradpilot.outlook;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Component;

import com.gradpilot.auth.JwtProperties;
import com.gradpilot.security.TokenCipher;

/**
 * The {@code state} value sent to Microsoft and back. It is signed, names the
 * person who started connecting and expires after ten minutes, so the
 * callback cannot be forged or replayed later. The PKCE verifier is derived
 * from it, so nothing has to be stored between the two steps.
 */
@Component
public class OAuthState {

    static final Duration TTL = Duration.ofMinutes(10);

    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder B64D = Base64.getUrlDecoder();

    private final byte[] key;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public OAuthState(JwtProperties jwt, Clock clock) {
        this.key = TokenCipher.derive(jwt.secret(), "gradpilot oauth state");
        this.clock = clock;
    }

    /** A new state for this person. */
    public String issue(UUID userId) {
        byte[] nonce = new byte[16];
        random.nextBytes(nonce);
        long expires = clock.instant().plus(TTL).getEpochSecond();
        String payload = userId + ":" + expires + ":" + B64.encodeToString(nonce);
        String encoded = B64.encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        return encoded + "." + B64.encodeToString(hmac(encoded));
    }

    /** The person who started connecting, if the state is genuine and still fresh. */
    public Optional<UUID> verify(String state) {
        if (state == null) {
            return Optional.empty();
        }
        int dot = state.indexOf('.');
        if (dot <= 0) {
            return Optional.empty();
        }
        String encoded = state.substring(0, dot);
        try {
            byte[] signature = B64D.decode(state.substring(dot + 1));
            if (!MessageDigest.isEqual(signature, hmac(encoded))) {
                return Optional.empty();
            }
            String[] parts = new String(B64D.decode(encoded), StandardCharsets.UTF_8).split(":");
            if (parts.length != 3 || Instant.ofEpochSecond(Long.parseLong(parts[1])).isBefore(clock.instant())) {
                return Optional.empty();
            }
            return Optional.of(UUID.fromString(parts[0]));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /** The PKCE code verifier for a state (43 characters, as RFC 7636 asks). */
    public String verifier(String state) {
        return B64.encodeToString(hmac("pkce:" + state));
    }

    /** The PKCE S256 challenge sent with the sign-in link. */
    public static String challenge(String verifier) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII));
            return B64.encodeToString(hash);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("SHA-256 is always available", e);
        }
    }

    private byte[] hmac(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC-SHA256 is always available", e);
        }
    }
}
