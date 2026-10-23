package com.gradpilot.push;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gradpilot.push.PushConfig.PushGateway;

@Service
public class PushService {

    private static final Logger log = LoggerFactory.getLogger(PushService.class);
    private static final int MAX_BODY = 600;

    private final PushSubscriptionRepository subscriptions;
    private final PushGateway gateway;
    private final PushProperties properties;
    private final ObjectMapper json;
    private final Clock clock;

    public PushService(PushSubscriptionRepository subscriptions, PushGateway gateway, PushProperties properties,
                       ObjectMapper json, Clock clock) {
        this.subscriptions = subscriptions;
        this.gateway = gateway;
        this.properties = properties;
        this.json = json;
        this.clock = clock;
    }

    public boolean enabled() {
        return gateway.enabled();
    }

    public String publicKey() {
        return gateway.enabled() ? gateway.sender().publicKey() : null;
    }

    @Transactional
    public void subscribe(UUID userId, String endpoint, String p256dh, String auth, String userAgent) {
        requireEnabled();
        validateEndpoint(endpoint);
        validateKeys(p256dh, auth);
        PushSubscription subscription = subscriptions.findByEndpoint(endpoint)
                .orElseGet(() -> new PushSubscription(userId, endpoint, p256dh, auth));
        subscription.reassign(userId, p256dh, auth);
        subscription.setUserAgent(userAgent == null ? null : truncate(userAgent, 300));
        subscriptions.save(subscription);
    }

    @Transactional
    public void unsubscribe(UUID userId, String endpoint) {
        subscriptions.findByUserIdAndEndpoint(userId, endpoint).ifPresent(subscriptions::delete);
    }

    @Transactional(readOnly = true)
    public int countDevices(UUID userId) {
        return subscriptions.findByUserId(userId).size();
    }

    /**
     * Sends a message to every device of one user.
     *
     * @return how many devices accepted it
     */
    @Transactional
    public int sendToUser(UUID userId, PushMessage message) {
        if (!gateway.enabled()) {
            return 0;
        }
        List<PushSubscription> devices = subscriptions.findByUserId(userId);
        if (devices.isEmpty()) {
            return 0;
        }
        byte[] payload = payload(message);
        int delivered = 0;
        for (PushSubscription device : devices) {
            try {
                int status = gateway.sender().send(device, payload, message.urgent());
                if (status >= 200 && status < 300) {
                    device.setLastSuccessAt(clock.instant());
                    delivered++;
                } else if (status == 404 || status == 410) {
                    // The person turned notifications off or removed the app
                    subscriptions.delete(device);
                } else {
                    log.warn("Push service answered {} for subscription {}", status, device.getId());
                }
            } catch (IOException e) {
                log.warn("Could not reach push service for subscription {}: {}", device.getId(), e.getMessage());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return delivered;
            }
        }
        return delivered;
    }

    byte[] payload(PushMessage message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", message.title());
        body.put("body", truncate(message.body(), MAX_BODY));
        body.put("url", message.url() == null ? "/" : message.url());
        if (message.tag() != null) {
            body.put("tag", message.tag());
        }
        try {
            return json.writeValueAsString(body).getBytes(StandardCharsets.UTF_8);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private void requireEnabled() {
        if (!gateway.enabled()) {
            throw new PushDisabledException();
        }
    }

    void validateEndpoint(String endpoint) {
        URI uri;
        try {
            uri = URI.create(endpoint);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Push endpoint is not a valid URL");
        }
        boolean secure = "https".equalsIgnoreCase(uri.getScheme());
        boolean insecureAllowed = properties.allowInsecure() && "http".equalsIgnoreCase(uri.getScheme());
        if (!(secure || insecureAllowed) || !properties.hostAllowed(uri.getHost())) {
            throw new IllegalArgumentException("Push endpoint must be a known push service");
        }
    }

    private static void validateKeys(String p256dh, String auth) {
        try {
            WebPushCrypto.publicKey(WebPushCrypto.decode(p256dh));
            if (WebPushCrypto.decode(auth).length != 16) {
                throw new IllegalArgumentException("auth secret must be 16 bytes");
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("Push subscription keys are invalid");
        }
    }

    private static String truncate(String value, int max) {
        if (value == null || value.length() <= max) {
            return value;
        }
        return value.substring(0, max - 1) + "…";
    }
}
