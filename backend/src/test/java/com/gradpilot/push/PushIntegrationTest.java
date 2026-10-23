package com.gradpilot.push;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.security.KeyPair;
import java.security.SecureRandom;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

import com.fasterxml.jackson.databind.JsonNode;
import com.gradpilot.ApiTestSupport;
import com.gradpilot.notify.NotificationService;
import com.gradpilot.tracker.Item;
import com.gradpilot.tracker.ItemRepository;
import com.sun.net.httpserver.HttpServer;

/**
 * End-to-end push: a fake push service on localhost receives what the server
 * sends, and the test decrypts it exactly as a phone would.
 */
@TestPropertySource(properties = {
        "gradpilot.push.allow-insecure=true",
        "gradpilot.push.allowed-hosts=localhost",
        "gradpilot.push.subject=mailto:test@example.com"
})
class PushIntegrationTest extends ApiTestSupport {

    private static final ZoneId MELBOURNE = ZoneId.of("Australia/Melbourne");
    private static final KeyPair VAPID = WebPushCrypto.generateKeyPair();
    private static final KeyPair BROWSER = WebPushCrypto.generateKeyPair();
    private static final byte[] AUTH = new byte[16];

    private record Received(String path, Map<String, List<String>> headers, byte[] body) {
    }

    private static final ConcurrentLinkedQueue<Received> RECEIVED = new ConcurrentLinkedQueue<>();
    private static final HttpServer PUSH_SERVICE = startFakePushService();

    static {
        new SecureRandom().nextBytes(AUTH);
    }

    @Autowired
    private NotificationService notifications;

    @Autowired
    private ItemRepository items;

    @DynamicPropertySource
    static void vapidKeys(DynamicPropertyRegistry registry) {
        registry.add("gradpilot.push.public-key",
                () -> WebPushCrypto.encode(WebPushCrypto.rawPublicKey((ECPublicKey) VAPID.getPublic())));
        registry.add("gradpilot.push.private-key",
                () -> WebPushCrypto.encode(WebPushCrypto.rawPrivateKey((ECPrivateKey) VAPID.getPrivate())));
    }

    private static HttpServer startFakePushService() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            server.createContext("/push/", exchange -> {
                byte[] body = exchange.getRequestBody().readAllBytes();
                String path = exchange.getRequestURI().getPath();
                RECEIVED.add(new Received(path, Map.copyOf(exchange.getRequestHeaders()), body));
                exchange.sendResponseHeaders(path.startsWith("/push/gone") ? 410 : 201, -1);
                exchange.close();
            });
            server.start();
            return server;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @AfterAll
    static void stopFakePushService() {
        PUSH_SERVICE.stop(0);
    }

    @BeforeEach
    void clear() {
        RECEIVED.clear();
    }

    private static String endpoint(String kind) {
        return "http://localhost:" + PUSH_SERVICE.getAddress().getPort() + "/push/" + kind + "/" + UUID.randomUUID();
    }

    private static Map<String, Object> subscription(String endpoint) {
        return Map.of("endpoint", endpoint, "keys", Map.of(
                "p256dh", WebPushCrypto.encode(WebPushCrypto.rawPublicKey((ECPublicKey) BROWSER.getPublic())),
                "auth", WebPushCrypto.encode(AUTH)));
    }

    private JsonNode decrypted(Received received) throws Exception {
        return json.readTree(WebPushTestSupport.decrypt(received.body(), BROWSER, AUTH));
    }

    private static String header(Received received, String name) {
        return received.headers().entrySet().stream()
                .filter(e -> e.getKey().equalsIgnoreCase(name))
                .map(e -> e.getValue().get(0))
                .findFirst().orElse(null);
    }

    @Test
    void statusShowsPublicKeyAndDeviceCount() throws Exception {
        String token = newUserToken();
        String expectedKey = WebPushCrypto.encode(WebPushCrypto.rawPublicKey((ECPublicKey) VAPID.getPublic()));

        getAs(token, "/api/push/status")
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.publicKey").value(expectedKey))
                .andExpect(jsonPath("$.devices").value(0));

        postAs(token, "/api/push/subscriptions", subscription(endpoint("ok"))).andExpect(status().isCreated());
        getAs(token, "/api/push/status").andExpect(jsonPath("$.devices").value(1));
    }

    @Test
    void testNotificationArrivesEncryptedWithVapidHeaders() throws Exception {
        String token = newUserToken();
        postAs(token, "/api/push/subscriptions", subscription(endpoint("ok"))).andExpect(status().isCreated());

        postAs(token, "/api/push/test", Map.of())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.delivered").value(1));

        assertThat(RECEIVED).hasSize(1);
        Received received = RECEIVED.peek();
        assertThat(header(received, "Content-Encoding")).isEqualTo("aes128gcm");
        assertThat(header(received, "Authorization")).startsWith("vapid t=");
        assertThat(header(received, "TTL")).isNotBlank();
        assertThat(decrypted(received).get("body").asText()).isEqualTo("Notifications are working.");
    }

    @Test
    void goneSubscriptionsAreRemoved() throws Exception {
        String token = newUserToken();
        postAs(token, "/api/push/subscriptions", subscription(endpoint("gone"))).andExpect(status().isCreated());

        postAs(token, "/api/push/test", Map.of()).andExpect(jsonPath("$.delivered").value(0));

        getAs(token, "/api/push/status").andExpect(jsonPath("$.devices").value(0));
    }

    @Test
    void rejectsUnknownHostsAndBadKeys() throws Exception {
        String token = newUserToken();

        postAs(token, "/api/push/subscriptions", subscription("https://evil.example.com/steal"))
                .andExpect(status().isBadRequest());
        postAs(token, "/api/push/subscriptions", subscription("http://169.254.169.254/latest"))
                .andExpect(status().isBadRequest());
        postAs(token, "/api/push/subscriptions", Map.of("endpoint", endpoint("ok"),
                "keys", Map.of("p256dh", "bm90LWEta2V5", "auth", "c2hvcnQ")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unsubscribeRemovesDevice() throws Exception {
        String token = newUserToken();
        String endpoint = endpoint("ok");
        postAs(token, "/api/push/subscriptions", subscription(endpoint));

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/push/subscriptions").param("endpoint", endpoint)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        getAs(token, "/api/push/status").andExpect(jsonPath("$.devices").value(0));
    }

    @Test
    void shiftReminderIsSentOnceWhenItIsTimeToLeave() throws Exception {
        String token = newUserToken();
        postAs(token, "/api/push/subscriptions", subscription(endpoint("ok")));
        String trackerId = body(postAs(token, "/api/trackers", Map.of("template", "work-shifts")))
                .at("/tracker/id").asText();
        String itemId = body(postAs(token, "/api/trackers/" + trackerId + "/items", Map.of(
                "title", "Kitchen shift", "itemDate", LocalDate.now(MELBOURNE).plusDays(3).toString(),
                "startTime", "16:00"))).get("id").asText();

        // Pretend the leave time has just arrived
        Item item = items.findById(UUID.fromString(itemId)).orElseThrow();
        item.setReminderAt(Instant.now().minusSeconds(30));
        items.save(item);

        assertThat(notifications.sendDueReminders()).isGreaterThanOrEqualTo(1);
        assertThat(notifications.sendDueReminders()).isZero();

        List<String> titles = RECEIVED.stream().map(r -> {
            try {
                return decrypted(r).get("title").asText();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }).toList();
        assertThat(titles).containsExactly("Time to leave");
        assertThat(items.findById(UUID.fromString(itemId)).orElseThrow().getReminderSentAt()).isNotNull();
    }

    @Test
    void morningPlanIsSentOncePerDay() throws Exception {
        ZonedDateTime now = ZonedDateTime.now(MELBOURNE);
        assumeTrue(now.toLocalTime().isAfter(LocalTime.of(0, 5)), "Too close to midnight in Melbourne");

        String token = newUserToken();
        postAs(token, "/api/push/subscriptions", subscription(endpoint("ok")));
        putAs(token, "/api/me", Map.of("morningTime", now.toLocalTime().minusMinutes(1).withSecond(0).withNano(0)
                .toString(), "displayName", "Nithya"));

        notifications.sendDailyDigests();
        notifications.sendDailyDigests();

        long mornings = RECEIVED.stream().filter(r -> {
            try {
                return decrypted(r).get("title").asText().equals("Good morning, Nithya");
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }).count();
        assertThat(mornings).isEqualTo(1);
    }

    @Test
    void pushEndpointsNeedLogin() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/push/status"))
                .andExpect(status().isUnauthorized());
    }
}
