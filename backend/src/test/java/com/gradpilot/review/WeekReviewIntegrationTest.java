package com.gradpilot.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.gradpilot.ApiTestSupport;

class WeekReviewIntegrationTest extends ApiTestSupport {

    private final LocalDate today = LocalDate.now(ZoneId.of("Australia/Melbourne"));

    @Test
    void summarisesTheLastSevenDays() throws Exception {
        String token = newUserToken();

        // The one thing, done today and the two days before
        for (int back = 0; back < 3; back++) {
            putAs(token, "/api/checkins/" + today.minusDays(back),
                    Map.of("focus", "Apply to two jobs", "focusDone", true)).andExpect(status().isOk());
        }

        // Two applications this week, one already at interview, one from last month
        postAs(token, "/api/jobs/emails", Map.of("text", """
                From: Canva Careers <careers@canva.com>
                Subject: Thanks for applying
                Thanks for applying for the Graduate Engineer role at Canva.
                """));
        postAs(token, "/api/jobs/emails", Map.of("text", """
                From: Atlassian <careers@atlassian.com>
                Subject: Interview invitation
                Thanks for applying for the Data Analyst role at Atlassian.
                We would like to invite you to a video interview.
                """));
        String jobs = body(getAs(token, "/api/trackers")).get(0).get("id").asText();
        postAs(token, "/api/trackers/" + jobs + "/items", Map.of(
                "title", "Old application", "stage", "Applied", "itemDate", today.minusDays(40).toString()));

        // LeetCode and shifts
        String leetcode = body(postAs(token, "/api/trackers", Map.of("template", "leetcode"))).at("/tracker/id").asText();
        postAs(token, "/api/trackers/" + leetcode + "/items", Map.of("title", "Solved", "amount", 3,
                "itemDate", today.toString()));
        postAs(token, "/api/trackers/" + leetcode + "/items", Map.of("title", "Solved", "amount", 2,
                "itemDate", today.minusDays(10).toString()));
        String shifts = body(postAs(token, "/api/trackers", Map.of("template", "work-shifts"))).at("/tracker/id").asText();
        postAs(token, "/api/trackers/" + shifts + "/items", Map.of("title", "Shift",
                "itemDate", today.minusDays(1).toString(), "startTime", "17:00", "endTime", "22:30"));

        JsonNode review = body(getAs(token, "/api/review/week").andExpect(status().isOk()));

        assertThat(review.get("start").asText()).isEqualTo(today.minusDays(6).toString());
        assertThat(review.get("end").asText()).isEqualTo(today.toString());
        assertThat(review.get("days")).hasSize(7);
        assertThat(review.get("focusDays").asInt()).isEqualTo(3);
        assertThat(review.get("streak").asInt()).isEqualTo(3);
        assertThat(review.at("/jobs/applied").asInt()).isEqualTo(2);
        assertThat(review.at("/jobs/interviews").asInt()).isEqualTo(1);
        assertThat(review.at("/jobs/active").asInt()).isEqualTo(3);
        assertThat(review.get("leetcodeSolved").asInt()).isEqualTo(3);
        assertThat(review.at("/shifts/count").asInt()).isEqualTo(1);
        assertThat(review.at("/shifts/hours").asDouble()).isEqualTo(5.5);
    }

    @Test
    void anEarlierWeekCanBeAskedFor() throws Exception {
        String token = newUserToken();
        JsonNode review = body(getAs(token, "/api/review/week?end=" + today.minusDays(7)));

        assertThat(review.get("end").asText()).isEqualTo(today.minusDays(7).toString());
        assertThat(review.get("focusDays").asInt()).isZero();
        assertThat(review.get("streak").asInt()).isZero();
    }
}
