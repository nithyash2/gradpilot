package com.gradpilot.push;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

class WebPushCryptoTest {

    private static byte[] randomBytes(int n) {
        byte[] b = new byte[n];
        new SecureRandom().nextBytes(b);
        return b;
    }

    @Test
    void browserCanDecryptWhatServerEncrypts() throws Exception {
        KeyPair browser = WebPushCrypto.generateKeyPair();
        byte[] auth = randomBytes(16);
        String p256dh = WebPushCrypto.encode(WebPushCrypto.rawPublicKey((ECPublicKey) browser.getPublic()));

        String message = "{\"title\":\"Time to leave\",\"body\":\"Kitchen shift starts at 4:00pm\"}";
        byte[] body = WebPushCrypto.encrypt(message.getBytes(StandardCharsets.UTF_8), p256dh,
                WebPushCrypto.encode(auth));

        // Header: 16 salt + 4 record size + 1 key length + 65 key
        assertThat(body.length).isEqualTo(86 + message.length() + 1 + 16);
        assertThat(WebPushTestSupport.decrypt(body, browser, auth)).isEqualTo(message);
    }

    @Test
    void everyMessageUsesFreshKeysAndSalt() throws Exception {
        KeyPair browser = WebPushCrypto.generateKeyPair();
        String p256dh = WebPushCrypto.encode(WebPushCrypto.rawPublicKey((ECPublicKey) browser.getPublic()));
        String auth = WebPushCrypto.encode(randomBytes(16));

        byte[] a = WebPushCrypto.encrypt("same".getBytes(StandardCharsets.UTF_8), p256dh, auth);
        byte[] b = WebPushCrypto.encrypt("same".getBytes(StandardCharsets.UTF_8), p256dh, auth);

        assertThat(a).isNotEqualTo(b);
    }

    @Test
    void rejectsPayloadThatDoesNotFitOneRecord() {
        KeyPair browser = WebPushCrypto.generateKeyPair();
        String p256dh = WebPushCrypto.encode(WebPushCrypto.rawPublicKey((ECPublicKey) browser.getPublic()));

        assertThatThrownBy(() -> WebPushCrypto.encrypt(new byte[WebPushCrypto.MAX_PAYLOAD + 1], p256dh,
                WebPushCrypto.encode(randomBytes(16))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void keysRoundTripThroughRawEncoding() throws Exception {
        KeyPair pair = WebPushCrypto.generateKeyPair();
        byte[] rawPublic = WebPushCrypto.rawPublicKey((ECPublicKey) pair.getPublic());
        byte[] rawPrivate = WebPushCrypto.rawPrivateKey((ECPrivateKey) pair.getPrivate());

        assertThat(rawPublic).hasSize(65).startsWith((byte) 4);
        assertThat(rawPrivate).hasSize(32);
        assertThat(WebPushCrypto.publicKey(rawPublic).getW()).isEqualTo(((ECPublicKey) pair.getPublic()).getW());
        assertThat(WebPushCrypto.privateKey(rawPrivate).getS()).isEqualTo(((ECPrivateKey) pair.getPrivate()).getS());
    }

    @Test
    void vapidTokenIsSignedForTheEndpointOrigin() throws Exception {
        KeyPair server = WebPushCrypto.generateKeyPair();
        String publicKey = WebPushCrypto.encode(WebPushCrypto.rawPublicKey((ECPublicKey) server.getPublic()));
        String privateKey = WebPushCrypto.encode(WebPushCrypto.rawPrivateKey((ECPrivateKey) server.getPrivate()));
        VapidKeys vapid = new VapidKeys(publicKey, privateKey, "mailto:me@example.com");

        String header = vapid.authorization(URI.create("https://web.push.apple.com/abc/def"),
                Instant.parse("2026-10-23T00:00:00Z"));

        assertThat(header).startsWith("vapid t=").endsWith(", k=" + publicKey);
        String token = header.substring("vapid t=".length(), header.indexOf(','));
        List<String> parts = List.of(token.split("\\."));
        String claims = new String(WebPushCrypto.decode(parts.get(1)), StandardCharsets.UTF_8);
        assertThat(claims).contains("\"aud\":\"https://web.push.apple.com\"")
                .contains("\"sub\":\"mailto:me@example.com\"")
                .contains("\"exp\":" + Instant.parse("2026-10-23T12:00:00Z").getEpochSecond());

        Signature verifier = Signature.getInstance("SHA256withECDSAinP1363Format");
        verifier.initVerify(server.getPublic());
        verifier.update((parts.get(0) + "." + parts.get(1)).getBytes(StandardCharsets.US_ASCII));
        assertThat(verifier.verify(WebPushCrypto.decode(parts.get(2)))).isTrue();
    }

    @Test
    void vapidRejectsBadKeysAndSubject() {
        KeyPair server = WebPushCrypto.generateKeyPair();
        String publicKey = WebPushCrypto.encode(WebPushCrypto.rawPublicKey((ECPublicKey) server.getPublic()));
        String privateKey = WebPushCrypto.encode(WebPushCrypto.rawPrivateKey((ECPrivateKey) server.getPrivate()));

        assertThatThrownBy(() -> new VapidKeys("not-a-key", privateKey, "mailto:me@example.com"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new VapidKeys(publicKey, privateKey, "me@example.com"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void onlyKnownPushHostsAreAllowed() {
        PushProperties properties = new PushProperties("k", "k", "mailto:x@example.com",
                List.of("web.push.apple.com", "*.notify.windows.com"), false);

        assertThat(properties.hostAllowed("web.push.apple.com")).isTrue();
        assertThat(properties.hostAllowed("wns2-sy3p.notify.windows.com")).isTrue();
        assertThat(properties.hostAllowed("notify.windows.com")).isFalse();
        assertThat(properties.hostAllowed("evil.example.com")).isFalse();
        assertThat(properties.hostAllowed("169.254.169.254")).isFalse();
        assertThat(properties.hostAllowed(null)).isFalse();
    }
}
