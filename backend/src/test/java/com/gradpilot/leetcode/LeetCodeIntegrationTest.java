package com.gradpilot.leetcode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gradpilot.ApiTestSupport;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/** LeetCode sync against a fake LeetCode that answers the profile query. */
class LeetCodeIntegrationTest extends ApiTestSupport {

    private static final HttpServer LEETCODE;
    /** Solved counts per username: easy, medium, hard. */
    private static final Map<String, int[]> PROFILES = new ConcurrentHashMap<>();

    static {
        try {
            LEETCODE = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        LEETCODE.createContext("/graphql", LeetCodeIntegrationTest::graphql);
        LEETCODE.start();
    }

    @DynamicPropertySource
    static void leetcode(DynamicPropertyRegistry registry) {
        registry.add("gradpilot.leetcode.base-url", () -> "http://127.0.0.1:" + LEETCODE.getAddress().getPort());
    }

    @AfterAll
    static void stop() {
        LEETCODE.stop(0);
    }

    @Test
    void firstSyncRecordsTheStartAndLaterSyncsLogNewProblems() throws Exception {
        String token = newUserToken();
        PROFILES.put("nithya_kp", new int[] {10, 4, 1});
        String id = body(postAs(token, "/api/trackers", Map.of(
                "template", "leetcode", "settings", Map.of("leetcodeUsername", "nithya_kp"))))
                .at("/tracker/id").asText();

        JsonNode first = body(postAs(token, "/api/trackers/" + id + "/leetcode/sync", Map.of())
                .andExpect(status().isOk()));
        assertThat(first.get("total").asInt()).isEqualTo(15);
        assertThat(first.get("newToday").asInt()).isZero();
        assertThat(body(getAs(token, "/api/trackers/" + id)).get("items")).isEmpty();

        PROFILES.put("nithya_kp", new int[] {11, 6, 1});
        JsonNode second = body(postAs(token, "/api/trackers/" + id + "/leetcode/sync", Map.of()));
        assertThat(second.get("newToday").asInt()).isEqualTo(3);

        PROFILES.put("nithya_kp", new int[] {12, 6, 1});
        JsonNode third = body(postAs(token, "/api/trackers/" + id + "/leetcode/sync", Map.of()));
        assertThat(third.get("newToday").asInt()).isEqualTo(4);

        JsonNode detail = body(getAs(token, "/api/trackers/" + id));
        assertThat(detail.get("items")).hasSize(1);
        assertThat(detail.get("items").get(0).get("amount").asInt()).isEqualTo(4);
        assertThat(detail.at("/tracker/settings/leetcode/medium").asInt()).isEqualTo(6);
        assertThat(detail.at("/tracker/stats/totalAmount").asInt()).isEqualTo(4);
    }

    @Test
    void changingTheUsernameStartsAgainWithoutCountingTheNewProfile() throws Exception {
        String token = newUserToken();
        PROFILES.put("first_name", new int[] {5, 0, 0});
        PROFILES.put("second_name", new int[] {200, 100, 20});
        String id = body(postAs(token, "/api/trackers", Map.of(
                "template", "leetcode", "settings", Map.of("leetcodeUsername", "first_name"))))
                .at("/tracker/id").asText();
        postAs(token, "/api/trackers/" + id + "/leetcode/sync", Map.of());

        JsonNode tracker = body(getAs(token, "/api/trackers/" + id)).get("tracker");
        Map<String, Object> settings = json.convertValue(tracker.get("settings"), Map.class);
        settings.put("leetcodeUsername", "second_name");
        putAs(token, "/api/trackers/" + id, Map.of("name", "LeetCode", "settings", settings))
                .andExpect(status().isOk());

        JsonNode result = body(postAs(token, "/api/trackers/" + id + "/leetcode/sync", Map.of()));

        assertThat(result.get("total").asInt()).isEqualTo(320);
        assertThat(result.get("newToday").asInt()).isZero();
    }

    @Test
    void explainsMissingUsersAndUsernames() throws Exception {
        String token = newUserToken();
        String noName = body(postAs(token, "/api/trackers", Map.of("template", "leetcode")))
                .at("/tracker/id").asText();
        assertThat(body(postAs(token, "/api/trackers/" + noName + "/leetcode/sync", Map.of()))
                .get("error").asText()).contains("username");

        String unknown = body(postAs(token, "/api/trackers", Map.of(
                "template", "leetcode", "settings", Map.of("leetcodeUsername", "nobody_here"))))
                .at("/tracker/id").asText();
        assertThat(body(postAs(token, "/api/trackers/" + unknown + "/leetcode/sync", Map.of()))
                .get("error").asText()).contains("no user called nobody_here");

        postAs(token, "/api/trackers", Map.of("template", "leetcode",
                "settings", Map.of("leetcodeUsername", "not valid!"))).andExpect(status().isBadRequest());
    }

    @Test
    void onlyTheOwnerCanSyncAndOnlyLeetCodeTrackers() throws Exception {
        String owner = newUserToken();
        String other = newUserToken();
        String id = body(postAs(owner, "/api/trackers", Map.of("template", "leetcode"))).at("/tracker/id").asText();
        String licence = body(postAs(owner, "/api/trackers", Map.of("template", "driving-licence")))
                .at("/tracker/id").asText();

        postAs(other, "/api/trackers/" + id + "/leetcode/sync", Map.of()).andExpect(status().isNotFound());
        postAs(owner, "/api/trackers/" + licence + "/leetcode/sync", Map.of()).andExpect(status().isBadRequest());
    }

    private static void graphql(HttpExchange exchange) throws IOException {
        JsonNode request = new ObjectMapper().readTree(exchange.getRequestBody().readAllBytes());
        String username = request.at("/variables/username").asText();
        int[] solved = PROFILES.get(username);
        String body;
        if (solved == null) {
            body = "{\"data\":{\"matchedUser\":null}}";
        } else {
            body = String.format("{\"data\":{\"matchedUser\":{\"submitStatsGlobal\":{\"acSubmissionNum\":["
                    + "{\"difficulty\":\"All\",\"count\":%d},{\"difficulty\":\"Easy\",\"count\":%d},"
                    + "{\"difficulty\":\"Medium\",\"count\":%d},{\"difficulty\":\"Hard\",\"count\":%d}]}}}}",
                    solved[0] + solved[1] + solved[2], solved[0], solved[1], solved[2]);
        }
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }
}
