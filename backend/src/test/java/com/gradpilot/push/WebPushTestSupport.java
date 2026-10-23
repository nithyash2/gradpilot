package com.gradpilot.push;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * The browser's side of RFC 8291: decrypts what the server sends,
 * so tests can check the exact message a phone would receive.
 */
public final class WebPushTestSupport {

    private WebPushTestSupport() {
    }

    public static String decrypt(byte[] body, KeyPair browserKeys, byte[] authSecret) throws Exception {
        ByteBuffer buffer = ByteBuffer.wrap(body);
        byte[] salt = new byte[16];
        buffer.get(salt);
        buffer.getInt(); // record size
        byte[] serverPublic = new byte[buffer.get() & 0xff];
        buffer.get(serverPublic);
        byte[] ciphertext = new byte[buffer.remaining()];
        buffer.get(ciphertext);

        byte[] browserPublic = WebPushCrypto.rawPublicKey((ECPublicKey) browserKeys.getPublic());
        byte[] shared = WebPushCrypto.ecdh((ECPrivateKey) browserKeys.getPrivate(),
                WebPushCrypto.publicKey(serverPublic));

        byte[] prkKey = WebPushCrypto.hmac(authSecret, shared);
        byte[] keyInfo = ByteBuffer.allocate(14 + 65 + 65 + 1)
                .put("WebPush: info".getBytes(StandardCharsets.US_ASCII)).put((byte) 0)
                .put(browserPublic).put(serverPublic).put((byte) 1).array();
        byte[] ikm = WebPushCrypto.hmac(prkKey, keyInfo);
        byte[] prk = WebPushCrypto.hmac(salt, ikm);
        byte[] cek = Arrays.copyOf(WebPushCrypto.hmac(prk, info("Content-Encoding: aes128gcm")), 16);
        byte[] nonce = Arrays.copyOf(WebPushCrypto.hmac(prk, info("Content-Encoding: nonce")), 12);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(cek, "AES"), new GCMParameterSpec(128, nonce));
        byte[] padded = cipher.doFinal(ciphertext);

        // Strip the 0x02 last-record delimiter and any zero padding after it
        int end = padded.length - 1;
        while (end >= 0 && padded[end] == 0) {
            end--;
        }
        if (end < 0 || padded[end] != 2) {
            throw new IllegalStateException("Missing last-record delimiter");
        }
        return new String(padded, 0, end, StandardCharsets.UTF_8);
    }

    private static byte[] info(String label) {
        byte[] bytes = label.getBytes(StandardCharsets.US_ASCII);
        byte[] out = Arrays.copyOf(bytes, bytes.length + 2);
        out[bytes.length + 1] = 1;
        return out;
    }
}
