package com.gradpilot.leetcode;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Reads how many problems someone has solved from their public LeetCode
 * profile, using the same GraphQL query the profile page uses. No password
 * or token is needed, only the username.
 */
@Component
public class LeetCodeClient {

    /** Solved counts by difficulty. */
    public record Solved(int easy, int medium, int hard) {
        public int total() {
            return easy + medium + hard;
        }
    }

    static final String QUERY = """
            query userSolved($username: String!) {
              matchedUser(username: $username) {
                submitStatsGlobal { acSubmissionNum { difficulty count } }
              }
            }""";

    private final ObjectMapper json;
    private final String baseUrl;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    public LeetCodeClient(ObjectMapper json,
                          @Value("${gradpilot.leetcode.base-url:https://leetcode.com}") String baseUrl) {
        this.json = json;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    /** Empty when there is no such user. */
    public Optional<Solved> solved(String username) throws IOException, InterruptedException {
        String body = json.writeValueAsString(Map.of("query", QUERY, "variables", Map.of("username", username)));
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/graphql"))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("Referer", baseUrl + "/u/" + username + "/")
                .header("User-Agent", "GradPilot (+https://github.com/nithyash2/gradpilot)")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("LeetCode answered " + response.statusCode());
        }
        JsonNode user = json.readTree(response.body()).path("data").path("matchedUser");
        if (user.isMissingNode() || user.isNull()) {
            return Optional.empty();
        }
        int easy = 0;
        int medium = 0;
        int hard = 0;
        for (JsonNode row : user.path("submitStatsGlobal").path("acSubmissionNum")) {
            int count = row.path("count").asInt(0);
            switch (row.path("difficulty").asText()) {
                case "Easy" -> easy = count;
                case "Medium" -> medium = count;
                case "Hard" -> hard = count;
                default -> {
                    // "All" is the sum of the others
                }
            }
        }
        return Optional.of(new Solved(easy, medium, hard));
    }
}
