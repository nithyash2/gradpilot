package com.gradpilot.outlook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.gradpilot.auth.JwtProperties;
import com.gradpilot.security.TokenCipher;

class OutlookSecurityTest {

    private static final JwtProperties JWT =
            new JwtProperties("a-test-secret-that-is-at-least-32-bytes-long", Duration.ofDays(7));

    @Test
    void tokensAreEncryptedDifferentlyEachTimeAndDecryptBack() {
        TokenCipher cipher = new TokenCipher(JWT);

        String first = cipher.encrypt("refresh-token");
        String second = cipher.encrypt("refresh-token");

        assertThat(first).isNotEqualTo(second).doesNotContain("refresh-token").startsWith("v1:");
        assertThat(cipher.decrypt(first)).isEqualTo("refresh-token");
        assertThat(cipher.decrypt(second)).isEqualTo("refresh-token");
    }

    @Test
    void tamperedOrForeignTokensAreRejected() {
        TokenCipher cipher = new TokenCipher(JWT);
        String sealed = cipher.encrypt("refresh-token");
        byte[] bytes = java.util.Base64.getDecoder().decode(sealed.substring(3));
        bytes[bytes.length / 2] ^= 1;
        String tampered = "v1:" + java.util.Base64.getEncoder().encodeToString(bytes);

        assertThatThrownBy(() -> cipher.decrypt(tampered)).isInstanceOf(IllegalArgumentException.class);
        TokenCipher otherServer = new TokenCipher(
                new JwtProperties("another-secret-that-is-at-least-32-bytes!!", Duration.ofDays(7)));
        assertThatThrownBy(() -> otherServer.decrypt(sealed)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void stateNamesThePersonAndExpires() {
        Instant start = Instant.parse("2026-10-23T06:00:00Z");
        UUID person = UUID.randomUUID();
        String state = new OAuthState(JWT, Clock.fixed(start, ZoneOffset.UTC)).issue(person);

        assertThat(new OAuthState(JWT, Clock.fixed(start.plusSeconds(60), ZoneOffset.UTC)).verify(state))
                .contains(person);
        assertThat(new OAuthState(JWT, Clock.fixed(start.plus(Duration.ofMinutes(11)), ZoneOffset.UTC)).verify(state))
                .isEmpty();
    }

    @Test
    void forgedStatesAreRejected() {
        OAuthState states = new OAuthState(JWT, Clock.systemUTC());
        String state = states.issue(UUID.randomUUID());
        String otherPerson = states.issue(UUID.randomUUID());
        String mixed = otherPerson.substring(0, otherPerson.indexOf('.')) + state.substring(state.indexOf('.'));

        assertThat(states.verify(mixed)).isEmpty();
        assertThat(states.verify("nonsense")).isEmpty();
        assertThat(states.verify(null)).isEmpty();
    }

    @Test
    void pkceVerifierIsStablePerStateAndMatchesTheChallengeFormat() {
        OAuthState states = new OAuthState(JWT, Clock.systemUTC());
        String state = states.issue(UUID.randomUUID());

        String verifier = states.verifier(state);

        assertThat(states.verifier(state)).isEqualTo(verifier);
        assertThat(verifier).hasSize(43).matches("[A-Za-z0-9_-]+");
        // RFC 7636 appendix B example
        assertThat(OAuthState.challenge("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"))
                .isEqualTo("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM");
    }
}
