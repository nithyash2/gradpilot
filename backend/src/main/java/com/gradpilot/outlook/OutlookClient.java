package com.gradpilot.outlook;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Talks to Microsoft: the sign-in endpoints (OAuth 2.0 authorization code
 * flow with PKCE) and Microsoft Graph for reading the inbox. Uses the JDK's
 * HTTP client, so there is no SDK to keep up to date.
 */
@Component
public class OutlookClient {

    static final String SCOPES = "offline_access User.Read Mail.Read";
    private static final Duration TIMEOUT = Duration.ofSeconds(20);
    private static final int PAGE_SIZE = 50;

    /** Tokens from Microsoft. The refresh token may be replaced on each refresh. */
    public record Tokens(String accessToken, String refreshToken, Instant expiresAt) {
    }

    /** One email from the inbox, with the body as plain text. */
    public record Message(String id, String internetMessageId, String subject, String fromName,
                          String fromAddress, Instant receivedAt, String text) {
    }

    /** Microsoft refused, for example because the person revoked access. */
    public static class OutlookException extends IOException {
        private final boolean reconnectNeeded;

        public OutlookException(String message, boolean reconnectNeeded) {
            super(message);
            this.reconnectNeeded = reconnectNeeded;
        }

        public boolean reconnectNeeded() {
            return reconnectNeeded;
        }
    }

    private final OutlookProperties properties;
    private final ObjectMapper json;
    private final HttpClient http;

    public OutlookClient(OutlookProperties properties, ObjectMapper json) {
        this.properties = properties;
        this.json = json;
        this.http = HttpClient.newBuilder()
                .connectTimeout(TIMEOUT)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    /** The Microsoft sign-in page that asks the person to allow GradPilot to read their mail. */
    public String authorizeUrl(String state, String codeChallenge) {
        Map<String, String> query = new LinkedHashMap<>();
        query.put("client_id", properties.clientId());
        query.put("response_type", "code");
        query.put("redirect_uri", properties.redirectUri());
        query.put("response_mode", "query");
        query.put("scope", SCOPES);
        query.put("state", state);
        query.put("code_challenge", codeChallenge);
        query.put("code_challenge_method", "S256");
        query.put("prompt", "select_account");
        return authority() + "/authorize?" + form(query);
    }

    public Tokens exchangeCode(String code, String codeVerifier) throws IOException, InterruptedException {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("client_id", properties.clientId());
        body.put("client_secret", properties.clientSecret());
        body.put("grant_type", "authorization_code");
        body.put("code", code);
        body.put("redirect_uri", properties.redirectUri());
        body.put("code_verifier", codeVerifier);
        body.put("scope", SCOPES);
        return tokens(body, null);
    }

    public Tokens refresh(String refreshToken) throws IOException, InterruptedException {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("client_id", properties.clientId());
        body.put("client_secret", properties.clientSecret());
        body.put("grant_type", "refresh_token");
        body.put("refresh_token", refreshToken);
        body.put("scope", SCOPES);
        return tokens(body, refreshToken);
    }

    /** The address of the mailbox the person connected. */
    public String mailboxAddress(String accessToken) throws IOException, InterruptedException {
        JsonNode me = getJson(properties.graphBase() + "/me?$select=mail,userPrincipalName", accessToken);
        String mail = me.path("mail").asText("");
        return mail.isBlank() ? me.path("userPrincipalName").asText(null) : mail;
    }

    /** Inbox emails received at or after {@code since}, oldest first, at most {@code max}. */
    public List<Message> inboxSince(String accessToken, Instant since, int max)
            throws IOException, InterruptedException {
        String filter = "receivedDateTime ge " + DateTimeFormatter.ISO_INSTANT.format(since);
        String url = properties.graphBase() + "/me/mailFolders/inbox/messages"
                + "?$select=" + encode("id,internetMessageId,subject,from,receivedDateTime,body")
                + "&$filter=" + encode(filter)
                + "&$orderby=" + encode("receivedDateTime asc")
                + "&$top=" + PAGE_SIZE;

        List<Message> messages = new ArrayList<>();
        while (url != null && messages.size() < max) {
            JsonNode page = getJson(url, accessToken);
            for (JsonNode node : page.path("value")) {
                if (messages.size() >= max) {
                    break;
                }
                messages.add(message(node));
            }
            String next = page.path("@odata.nextLink").asText(null);
            // Only ever follow links back to Microsoft Graph
            url = next != null && next.startsWith(properties.graphBase() + "/") ? next : null;
        }
        return messages;
    }

    // ---- Helpers --------------------------------------------------------

    private Tokens tokens(Map<String, String> body, String previousRefreshToken)
            throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(authority() + "/token"))
                .timeout(TIMEOUT)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(form(body)))
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        JsonNode node = parse(response.body());
        if (response.statusCode() != 200 || !node.hasNonNull("access_token")) {
            String error = node.path("error").asText("http_" + response.statusCode());
            boolean reconnect = error.equals("invalid_grant") || error.equals("interaction_required")
                    || error.equals("consent_required");
            throw new OutlookException("Microsoft sign-in failed: " + error, reconnect);
        }
        String refresh = node.path("refresh_token").asText(null);
        long expiresIn = node.path("expires_in").asLong(3600);
        return new Tokens(node.get("access_token").asText(),
                refresh != null ? refresh : previousRefreshToken,
                Instant.now().plusSeconds(Math.max(60, expiresIn - 60)));
    }

    private JsonNode getJson(String url, String accessToken) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(TIMEOUT)
                .header("Authorization", "Bearer " + accessToken)
                .header("Accept", "application/json")
                .header("Prefer", "outlook.body-content-type=\"text\"")
                .GET()
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 401 || response.statusCode() == 403) {
            throw new OutlookException("Microsoft Graph refused access (" + response.statusCode() + ")",
                    response.statusCode() == 403);
        }
        if (response.statusCode() != 200) {
            throw new OutlookException("Microsoft Graph answered " + response.statusCode(), false);
        }
        return parse(response.body());
    }

    private Message message(JsonNode node) {
        JsonNode from = node.path("from").path("emailAddress");
        String body = node.path("body").path("content").asText("");
        if ("html".equalsIgnoreCase(node.path("body").path("contentType").asText())) {
            body = com.gradpilot.jobs.EmailText.fromHtml(body);
        }
        String received = node.path("receivedDateTime").asText(null);
        return new Message(
                node.path("id").asText(),
                node.path("internetMessageId").asText(null),
                node.path("subject").asText(""),
                from.path("name").asText(null),
                from.path("address").asText(null),
                received == null ? null : Instant.parse(received),
                body);
    }

    private JsonNode parse(String body) throws IOException {
        if (body == null || body.isBlank()) {
            return json.createObjectNode();
        }
        try {
            return json.readTree(body);
        } catch (IOException e) {
            throw new OutlookException("Microsoft sent an unreadable answer", false);
        }
    }

    private String authority() {
        return properties.authorityBase() + "/" + properties.tenant() + "/oauth2/v2.0";
    }

    private static String form(Map<String, String> values) {
        return values.entrySet().stream()
                .map(e -> encode(e.getKey()) + "=" + encode(e.getValue()))
                .collect(Collectors.joining("&"));
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
