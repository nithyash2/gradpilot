package com.gradpilot.push;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.Signature;
import java.security.interfaces.ECPrivateKey;
import java.time.Duration;
import java.time.Instant;

/**
 * The server's VAPID identity (RFC 8292). Push services use it to check that
 * messages come from the same server that the browser subscribed with.
 */
public final class VapidKeys {

    static final Duration TOKEN_LIFETIME = Duration.ofHours(12);

    private final String publicKey;
    private final ECPrivateKey privateKey;
    private final String subject;

    /**
     * @param publicKey  65 byte uncompressed P-256 public key, base64url
     * @param privateKey 32 byte P-256 private key, base64url
     * @param subject    contact for the push service: a mailto: or https: URL
     */
    public VapidKeys(String publicKey, String privateKey, String subject) {
        try {
            byte[] rawPublic = WebPushCrypto.decode(publicKey);
            this.privateKey = WebPushCrypto.privateKey(WebPushCrypto.decode(privateKey));
            // Validates the public key and normalises its encoding
            this.publicKey = WebPushCrypto.encode(WebPushCrypto.rawPublicKey(WebPushCrypto.publicKey(rawPublic)));
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid VAPID keys: " + e.getMessage(), e);
        }
        if (subject == null || !(subject.startsWith("mailto:") || subject.startsWith("https://"))) {
            throw new IllegalArgumentException("VAPID subject must start with mailto: or https://");
        }
        this.subject = subject;
    }

    public String publicKey() {
        return publicKey;
    }

    /** The value of the Authorization header for a request to {@code endpoint}. */
    public String authorization(URI endpoint, Instant now) {
        return "vapid t=" + token(audience(endpoint), now) + ", k=" + publicKey;
    }

    static String audience(URI endpoint) {
        String origin = endpoint.getScheme() + "://" + endpoint.getHost();
        return endpoint.getPort() == -1 ? origin : origin + ":" + endpoint.getPort();
    }

    String token(String audience, Instant now) {
        String header = "{\"typ\":\"JWT\",\"alg\":\"ES256\"}";
        long expires = now.plus(TOKEN_LIFETIME).getEpochSecond();
        String claims = "{\"aud\":\"" + escape(audience) + "\",\"exp\":" + expires
                + ",\"sub\":\"" + escape(subject) + "\"}";
        String unsigned = base64(header) + "." + base64(claims);
        try {
            // P1363 format gives the raw 64 byte r||s signature that JWS ES256 expects
            Signature signer = Signature.getInstance("SHA256withECDSAinP1363Format");
            signer.initSign(privateKey);
            signer.update(unsigned.getBytes(StandardCharsets.US_ASCII));
            return unsigned + "." + WebPushCrypto.encode(signer.sign());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Could not sign VAPID token", e);
        }
    }

    private static String base64(String json) {
        return WebPushCrypto.encode(json.getBytes(StandardCharsets.UTF_8));
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
