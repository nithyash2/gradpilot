package com.gradpilot.push;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;

/**
 * Sends one encrypted message to one push endpoint (RFC 8030).
 */
public class PushSender {

    static final Duration TIME_TO_LIVE = Duration.ofHours(12);

    private final VapidKeys vapid;
    private final HttpClient http;
    private final Clock clock;

    public PushSender(VapidKeys vapid, Clock clock) {
        this.vapid = vapid;
        this.clock = clock;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    public String publicKey() {
        return vapid.publicKey();
    }

    /**
     * @return the push service's HTTP status: 201 means accepted,
     *         404 or 410 mean the subscription has gone and should be removed
     */
    public int send(PushSubscription subscription, byte[] payload, boolean urgent)
            throws IOException, InterruptedException {
        URI endpoint = URI.create(subscription.getEndpoint());
        byte[] body = WebPushCrypto.encrypt(payload, subscription.getP256dh(), subscription.getAuth());

        HttpRequest request = HttpRequest.newBuilder(endpoint)
                .timeout(Duration.ofSeconds(15))
                .header("TTL", Long.toString(TIME_TO_LIVE.toSeconds()))
                .header("Urgency", urgent ? "high" : "normal")
                .header("Content-Type", "application/octet-stream")
                .header("Content-Encoding", "aes128gcm")
                .header("Authorization", vapid.authorization(endpoint, clock.instant()))
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();
        return http.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
    }
}
