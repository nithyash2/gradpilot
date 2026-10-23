package com.gradpilot.outlook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.fasterxml.jackson.databind.JsonNode;
import com.gradpilot.ApiTestSupport;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * The whole Outlook flow against a fake Microsoft: connect with OAuth and
 * PKCE, read the inbox, import job emails once, and recover from revoked access.
 */
class OutlookIntegrationTest extends ApiTestSupport {

    private static final HttpServer MICROSOFT;
    private static final List<Map<String, Object>> INBOX = new ArrayList<>();
    private static volatile String lastVerifier;
    private static volatile String lastRefreshToken;

    static {
        try {
            MICROSOFT = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        MICROSOFT.createContext("/common/oauth2/v2.0/token", OutlookIntegrationTest::token);
        MICROSOFT.createContext("/graph/me/mailFolders/inbox/messages", OutlookIntegrationTest::inbox);
        MICROSOFT.createContext("/graph/me", exchange -> send(exchange, 200, "{\"mail\":\"kp@outlook.com\"}"));
        MICROSOFT.start();
    }

    @DynamicPropertySource
    static void outlook(DynamicPropertyRegistry registry) {
        String base = "http://127.0.0.1:" + MICROSOFT.getAddress().getPort();
        registry.add("gradpilot.outlook.client-id", () -> "client-123");
        registry.add("gradpilot.outlook.client-secret", () -> "secret-456");
        registry.add("gradpilot.outlook.redirect-uri", () -> "https://api.example/api/outlook/callback");
        registry.add("gradpilot.outlook.web-app-url", () -> "https://app.example");
        registry.add("gradpilot.outlook.authority-base", () -> base);
        registry.add("gradpilot.outlook.graph-base", () -> base + "/graph");
    }

    @AfterAll
    static void stop() {
        MICROSOFT.stop(0);
    }

    @Autowired
    private MailboxConnectionRepository connections;

    @Autowired
    private TokenCipher cipher;

    @BeforeEach
    void fillInbox() {
        Instant now = Instant.now();
        INBOX.clear();
        INBOX.add(message("m1", "Canva Careers", "careers@canva.com", "Thanks for applying",
                "Thanks for applying for the Graduate Software Engineer role at Canva.", now.minusSeconds(7200)));
        INBOX.add(message("m2", "Coles", "specials@coles.com.au", "This week's specials",
                "Half price snacks. Unfortunately stock is limited.", now.minusSeconds(5400)));
        INBOX.add(message("m3", "Atlassian Recruiting", "no-reply@greenhouse.io", "Interview invitation",
                "Thank you for applying for the Associate Engineer role at Atlassian. "
                        + "We would like to invite you to a video interview.", now.minusSeconds(3600)));
    }

    @Test
    void connectsReadsTheInboxAndImportsEachEmailOnce() throws Exception {
        String token = newUserToken();

        JsonNode before = body(getAs(token, "/api/outlook/status"));
        assertThat(before.get("available").asBoolean()).isTrue();
        assertThat(before.get("connected").asBoolean()).isFalse();

        // 1. The web app asks for the Microsoft sign-in link
        String url = body(postAs(token, "/api/outlook/connect", Map.of())).get("url").asText();
        Map<String, String> query = query(url);
        assertThat(url).startsWith("http://127.0.0.1:" + MICROSOFT.getAddress().getPort() + "/common/oauth2/v2.0/authorize?");
        assertThat(query).containsEntry("client_id", "client-123")
                .containsEntry("code_challenge_method", "S256")
                .containsEntry("scope", "offline_access User.Read Mail.Read");

        // 2. Microsoft sends the browser back with a code
        String location = mvc.perform(get("/api/outlook/callback")
                        .param("code", "good-code").param("state", query.get("state")))
                .andExpect(status().isFound())
                .andReturn().getResponse().getHeader("Location");
        assertThat(location).isEqualTo("https://app.example/settings?outlook=connected");
        assertThat(OAuthState.challenge(lastVerifier)).isEqualTo(query.get("code_challenge"));

        JsonNode connected = body(getAs(token, "/api/outlook/status"));
        assertThat(connected.get("connected").asBoolean()).isTrue();
        assertThat(connected.get("email").asText()).isEqualTo("kp@outlook.com");

        // Tokens are never stored as plain text
        UUID userId = UUID.fromString(body(getAs(token, "/api/me")).get("id").asText());
        MailboxConnection stored = connections.findByUserId(userId).orElseThrow();
        assertThat(stored.getRefreshToken()).doesNotContain("refresh-1");
        assertThat(cipher.decrypt(stored.getRefreshToken())).isEqualTo("refresh-1");

        // 3. Sync: two job emails, one shop email
        JsonNode first = body(postAs(token, "/api/outlook/sync", Map.of()));
        assertThat(first.get("read").asInt()).isEqualTo(3);
        assertThat(first.get("created").asInt()).isEqualTo(2);

        JsonNode trackers = body(getAs(token, "/api/trackers"));
        assertThat(trackers).hasSize(1);
        JsonNode items = body(getAs(token, "/api/trackers/" + trackers.get(0).get("id").asText())).get("items");
        assertThat(items).extracting(i -> i.get("title").asText())
                .containsExactlyInAnyOrder("Canva – Graduate Software Engineer", "Atlassian – Associate Engineer");
        assertThat(items).extracting(i -> i.get("stage").asText()).containsExactlyInAnyOrder("Applied", "Interview");

        // 4. Syncing again adds nothing new
        JsonNode second = body(postAs(token, "/api/outlook/sync", Map.of()));
        assertThat(second.get("created").asInt()).isZero();
        assertThat(second.get("updated").asInt()).isZero();

        // 5. Disconnect
        deleteAs(token, "/api/outlook").andExpect(status().isNoContent());
        assertThat(body(getAs(token, "/api/outlook/status")).get("connected").asBoolean()).isFalse();
    }

    @Test
    void forgedOrMissingStateIsRefused() throws Exception {
        String location = mvc.perform(get("/api/outlook/callback")
                        .param("code", "good-code").param("state", "not-a-real-state"))
                .andExpect(status().isFound())
                .andReturn().getResponse().getHeader("Location");

        assertThat(location).isEqualTo("https://app.example/settings?outlook=error&reason=expired");
    }

    @Test
    void declinedConsentGoesBackToTheApp() throws Exception {
        String token = newUserToken();
        String state = query(body(postAs(token, "/api/outlook/connect", Map.of())).get("url").asText()).get("state");

        String location = mvc.perform(get("/api/outlook/callback")
                        .param("error", "access_denied").param("state", state))
                .andReturn().getResponse().getHeader("Location");

        assertThat(location).isEqualTo("https://app.example/settings?outlook=error&reason=declined");
        assertThat(body(getAs(token, "/api/outlook/status")).get("connected").asBoolean()).isFalse();
    }

    @Test
    void revokedAccessAsksThePersonToConnectAgain() throws Exception {
        String token = newUserToken();
        String state = query(body(postAs(token, "/api/outlook/connect", Map.of())).get("url").asText()).get("state");
        mvc.perform(get("/api/outlook/callback").param("code", "good-code").param("state", state));

        UUID userId = UUID.fromString(body(getAs(token, "/api/me")).get("id").asText());
        MailboxConnection connection = connections.findByUserId(userId).orElseThrow();
        connection.setAccessExpiresAt(Instant.now().minusSeconds(60));
        connection.setRefreshToken(cipher.encrypt("revoked"));
        connections.save(connection);

        JsonNode result = body(postAs(token, "/api/outlook/sync", Map.of()));

        assertThat(result.get("error").asText()).contains("Connect it again");
        JsonNode status = body(getAs(token, "/api/outlook/status"));
        assertThat(status.get("needsReconnect").asBoolean()).isTrue();
        assertThat(lastRefreshToken).isEqualTo("revoked");
    }

    @Test
    void syncNeedsAConnection() throws Exception {
        String token = newUserToken();
        postAs(token, "/api/outlook/sync", Map.of()).andExpect(status().isConflict());
        postAs(token, "/api/outlook/connect", Map.of()).andExpect(status().isOk());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/outlook/connect"))
                .andExpect(status().isUnauthorized());
    }

    // ---- Fake Microsoft -------------------------------------------------

    private static void token(HttpExchange exchange) throws IOException {
        Map<String, String> form = parseForm(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        if (!"client-123".equals(form.get("client_id")) || !"secret-456".equals(form.get("client_secret"))) {
            send(exchange, 401, "{\"error\":\"invalid_client\"}");
            return;
        }
        if ("authorization_code".equals(form.get("grant_type")) && "good-code".equals(form.get("code"))) {
            lastVerifier = form.get("code_verifier");
            send(exchange, 200, "{\"access_token\":\"access-1\",\"refresh_token\":\"refresh-1\",\"expires_in\":3600}");
        } else if ("refresh_token".equals(form.get("grant_type"))) {
            lastRefreshToken = form.get("refresh_token");
            if ("revoked".equals(form.get("refresh_token"))) {
                send(exchange, 400, "{\"error\":\"invalid_grant\"}");
            } else {
                send(exchange, 200, "{\"access_token\":\"access-2\",\"refresh_token\":\"refresh-2\",\"expires_in\":3600}");
            }
        } else {
            send(exchange, 400, "{\"error\":\"invalid_grant\"}");
        }
    }

    private static void inbox(HttpExchange exchange) throws IOException {
        String auth = exchange.getRequestHeaders().getFirst("Authorization");
        if (auth == null || !auth.startsWith("Bearer access-")) {
            send(exchange, 401, "{}");
            return;
        }
        StringBuilder value = new StringBuilder("[");
        for (int i = 0; i < INBOX.size(); i++) {
            Map<String, Object> m = INBOX.get(i);
            if (i > 0) {
                value.append(',');
            }
            value.append(String.format(
                    "{\"id\":\"%s\",\"internetMessageId\":\"<%s@mail>\",\"subject\":\"%s\","
                            + "\"from\":{\"emailAddress\":{\"name\":\"%s\",\"address\":\"%s\"}},"
                            + "\"receivedDateTime\":\"%s\",\"body\":{\"contentType\":\"text\",\"content\":\"%s\"}}",
                    m.get("id"), m.get("id"), m.get("subject"), m.get("name"), m.get("address"),
                    m.get("received"), m.get("text")));
        }
        value.append(']');
        send(exchange, 200, "{\"value\":" + value + "}");
    }

    private static Map<String, Object> message(String id, String name, String address, String subject,
                                               String text, Instant received) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("name", name);
        m.put("address", address);
        m.put("subject", subject);
        m.put("text", text);
        m.put("received", received.toString());
        return m;
    }

    private static void send(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private static Map<String, String> query(String url) {
        return parseForm(URI.create(url).getRawQuery());
    }

    private static Map<String, String> parseForm(String raw) {
        Map<String, String> values = new LinkedHashMap<>();
        if (raw == null) {
            return values;
        }
        for (String pair : raw.split("&")) {
            int eq = pair.indexOf('=');
            if (eq > 0) {
                values.put(URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8),
                        URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8));
            }
        }
        return values;
    }
}
