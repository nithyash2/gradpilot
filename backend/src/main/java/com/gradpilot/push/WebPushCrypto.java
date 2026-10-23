package com.gradpilot.push;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPrivateKeySpec;
import java.security.spec.ECPublicKeySpec;
import java.util.Arrays;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Encrypts push messages as described in RFC 8291 (Message Encryption for
 * Web Push) using the aes128gcm content coding from RFC 8188.
 *
 * <p>Uses only the JDK: P-256 ECDH, HKDF built from HMAC-SHA256, and AES-128-GCM.
 */
public final class WebPushCrypto {

    /** Record size written in the header. One record holds the whole message. */
    static final int RECORD_SIZE = 4096;
    private static final int KEY_LENGTH = 65;
    private static final int SALT_LENGTH = 16;
    private static final int TAG_LENGTH = 16;
    /** The largest payload that fits in a single record. */
    public static final int MAX_PAYLOAD = RECORD_SIZE - TAG_LENGTH - 1;

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final ECParameterSpec P256 = p256();

    private WebPushCrypto() {
    }

    /**
     * Encrypts {@code payload} for one browser subscription.
     *
     * @param payload       message bytes, at most {@link #MAX_PAYLOAD}
     * @param userPublicKey the subscription's {@code p256dh} key, base64url
     * @param authSecret    the subscription's {@code auth} secret, base64url
     * @return the request body to POST to the push endpoint
     */
    public static byte[] encrypt(byte[] payload, String userPublicKey, String authSecret) {
        byte[] salt = new byte[SALT_LENGTH];
        RANDOM.nextBytes(salt);
        return encrypt(payload, decode(userPublicKey), decode(authSecret), generateKeyPair(), salt);
    }

    static byte[] encrypt(byte[] payload, byte[] uaPublic, byte[] authSecret, KeyPair serverKeys, byte[] salt) {
        if (payload.length > MAX_PAYLOAD) {
            throw new IllegalArgumentException("Push payload is too large: " + payload.length + " bytes");
        }
        try {
            byte[] asPublic = rawPublicKey((ECPublicKey) serverKeys.getPublic());
            byte[] sharedSecret = ecdh((ECPrivateKey) serverKeys.getPrivate(), publicKey(uaPublic));

            // RFC 8291 section 3.4: combine the shared secret with the auth secret
            byte[] prkKey = hmac(authSecret, sharedSecret);
            byte[] keyInfo = concat("WebPush: info".getBytes(StandardCharsets.US_ASCII), new byte[] {0},
                    uaPublic, asPublic, new byte[] {1});
            byte[] ikm = hmac(prkKey, keyInfo);

            // RFC 8188 section 2.2: derive the content key and nonce
            byte[] prk = hmac(salt, ikm);
            byte[] cek = Arrays.copyOf(hmac(prk, info("Content-Encoding: aes128gcm")), 16);
            byte[] nonce = Arrays.copyOf(hmac(prk, info("Content-Encoding: nonce")), 12);

            // Single record: payload followed by the 0x02 "last record" delimiter
            byte[] plaintext = Arrays.copyOf(payload, payload.length + 1);
            plaintext[payload.length] = 2;

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(cek, "AES"), new GCMParameterSpec(128, nonce));
            byte[] ciphertext = cipher.doFinal(plaintext);

            ByteBuffer body = ByteBuffer.allocate(SALT_LENGTH + 4 + 1 + KEY_LENGTH + ciphertext.length);
            body.put(salt).putInt(RECORD_SIZE).put((byte) KEY_LENGTH).put(asPublic).put(ciphertext);
            return body.array();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Could not encrypt push message", e);
        }
    }

    // ---- Key helpers ----------------------------------------------------

    public static KeyPair generateKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
            generator.initialize(new ECGenParameterSpec("secp256r1"), RANDOM);
            return generator.generateKeyPair();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("P-256 is not available", e);
        }
    }

    /** Uncompressed point: 0x04 || X || Y, 65 bytes. */
    public static byte[] rawPublicKey(ECPublicKey key) {
        byte[] raw = new byte[KEY_LENGTH];
        raw[0] = 4;
        System.arraycopy(unsigned32(key.getW().getAffineX()), 0, raw, 1, 32);
        System.arraycopy(unsigned32(key.getW().getAffineY()), 0, raw, 33, 32);
        return raw;
    }

    /** Raw private scalar, 32 bytes. */
    public static byte[] rawPrivateKey(ECPrivateKey key) {
        return unsigned32(key.getS());
    }

    public static ECPublicKey publicKey(byte[] raw) throws GeneralSecurityException {
        if (raw.length != KEY_LENGTH || raw[0] != 4) {
            throw new GeneralSecurityException("Expected a 65 byte uncompressed P-256 public key");
        }
        BigInteger x = new BigInteger(1, Arrays.copyOfRange(raw, 1, 33));
        BigInteger y = new BigInteger(1, Arrays.copyOfRange(raw, 33, 65));
        return (ECPublicKey) KeyFactory.getInstance("EC")
                .generatePublic(new ECPublicKeySpec(new ECPoint(x, y), P256));
    }

    public static ECPrivateKey privateKey(byte[] raw) throws GeneralSecurityException {
        if (raw.length != 32) {
            throw new GeneralSecurityException("Expected a 32 byte P-256 private key");
        }
        return (ECPrivateKey) KeyFactory.getInstance("EC")
                .generatePrivate(new ECPrivateKeySpec(new BigInteger(1, raw), P256));
    }

    static byte[] ecdh(ECPrivateKey privateKey, ECPublicKey publicKey) throws GeneralSecurityException {
        KeyAgreement agreement = KeyAgreement.getInstance("ECDH");
        agreement.init(privateKey);
        agreement.doPhase(publicKey, true);
        return agreement.generateSecret();
    }

    static byte[] hmac(byte[] key, byte[] data) throws GeneralSecurityException {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(data);
    }

    public static byte[] decode(String base64Url) {
        return Base64.getUrlDecoder().decode(base64Url.trim());
    }

    public static String encode(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static byte[] info(String label) {
        return concat(label.getBytes(StandardCharsets.US_ASCII), new byte[] {0, 1});
    }

    private static byte[] concat(byte[]... parts) {
        int length = 0;
        for (byte[] part : parts) {
            length += part.length;
        }
        ByteBuffer buffer = ByteBuffer.allocate(length);
        for (byte[] part : parts) {
            buffer.put(part);
        }
        return buffer.array();
    }

    private static byte[] unsigned32(BigInteger value) {
        byte[] bytes = value.toByteArray();
        byte[] out = new byte[32];
        int copy = Math.min(bytes.length, 32);
        System.arraycopy(bytes, bytes.length - copy, out, 32 - copy, copy);
        return out;
    }

    private static ECParameterSpec p256() {
        try {
            AlgorithmParameters parameters = AlgorithmParameters.getInstance("EC");
            parameters.init(new ECGenParameterSpec("secp256r1"));
            return parameters.getParameterSpec(ECParameterSpec.class);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("P-256 is not available", e);
        }
    }
}
