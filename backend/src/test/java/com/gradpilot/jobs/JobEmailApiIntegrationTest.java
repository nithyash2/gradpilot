package com.gradpilot.jobs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.gradpilot.ApiTestSupport;

class JobEmailApiIntegrationTest extends ApiTestSupport {

    private static final String CONFIRMATION = """
            From: Canva Careers <careers@canva.com>
            Subject: Thanks for applying to Canva

            Hi Nithyashree,
            Thanks for applying for the Graduate Software Engineer role at Canva.
            If you are shortlisted, we will be in touch to arrange an interview.
            """;

    private static final String INTERVIEW = """
            From: Canva Careers <careers@canva.com>
            Subject: Interview invitation

            Hi Nithyashree,
            Thanks for applying for the Graduate Software Engineer role at Canva.
            We would love to invite you to a video interview next week.
            """;

    private static final String REJECTION = """
            From: Canva Careers <careers@canva.com>
            Subject: Your application to Canva

            Thank you for your interest in the Graduate Software Engineer role at Canva.
            Unfortunately we have decided to move forward with other candidates.
            """;

    @Test
    void confirmationCreatesTheJobHuntTrackerAndAnApplication() throws Exception {
        String token = newUserToken();

        JsonNode result = body(postAs(token, "/api/jobs/emails", Map.of("text", CONFIRMATION))
                .andExpect(status().isOk()));

        assertThat(result.get("outcome").asText()).isEqualTo("CREATED");
        assertThat(result.get("kind").asText()).isEqualTo("APPLIED");
        assertThat(result.get("company").asText()).isEqualTo("Canva");
        assertThat(result.get("role").asText()).isEqualTo("Graduate Software Engineer");
        assertThat(result.get("stage").asText()).isEqualTo("Applied");
        assertThat(result.get("title").asText()).isEqualTo("Canva – Graduate Software Engineer");

        JsonNode tracker = body(getAs(token, "/api/trackers/" + result.get("trackerId").asText()));
        assertThat(tracker.at("/tracker/template").asText()).isEqualTo("job-hunt");
        assertThat(tracker.get("items")).hasSize(1);
        JsonNode item = tracker.get("items").get(0);
        assertThat(item.at("/fields/company").asText()).isEqualTo("Canva");
        assertThat(item.at("/fields/source").asText()).isEqualTo("Company site");
        assertThat(item.get("notes").asText()).contains("Application confirmed");
    }

    @Test
    void laterEmailsMoveTheSameApplicationForward() throws Exception {
        String token = newUserToken();
        String itemId = body(postAs(token, "/api/jobs/emails", Map.of("text", CONFIRMATION)))
                .get("itemId").asText();

        JsonNode interview = body(postAs(token, "/api/jobs/emails", Map.of("text", INTERVIEW)));
        assertThat(interview.get("outcome").asText()).isEqualTo("UPDATED");
        assertThat(interview.get("itemId").asText()).isEqualTo(itemId);
        assertThat(interview.get("previousStage").asText()).isEqualTo("Applied");
        assertThat(interview.get("stage").asText()).isEqualTo("Interview");

        // A late confirmation never moves an application backwards
        JsonNode again = body(postAs(token, "/api/jobs/emails",
                Map.of("text", CONFIRMATION.replace("Hi Nithyashree", "Hello Nithyashree"))));
        assertThat(again.get("outcome").asText()).isEqualTo("UNCHANGED");
        assertThat(again.get("stage").asText()).isEqualTo("Interview");

        JsonNode rejected = body(postAs(token, "/api/jobs/emails", Map.of("text", REJECTION)));
        assertThat(rejected.get("outcome").asText()).isEqualTo("UPDATED");
        assertThat(rejected.get("stage").asText()).isEqualTo("Rejected");
    }

    @Test
    void theSameEmailIsOnlyCountedOnce() throws Exception {
        String token = newUserToken();
        postAs(token, "/api/jobs/emails", Map.of("text", CONFIRMATION));

        JsonNode second = body(postAs(token, "/api/jobs/emails", Map.of("text", CONFIRMATION)));

        assertThat(second.get("outcome").asText()).isEqualTo("DUPLICATE");
        JsonNode tracker = body(getAs(token, "/api/trackers/" + second.get("trackerId").asText()));
        assertThat(tracker.get("items")).hasSize(1);
    }

    @Test
    void differentRolesAtOneCompanyAreSeparateApplications() throws Exception {
        String token = newUserToken();
        postAs(token, "/api/jobs/emails", Map.of("text", CONFIRMATION));

        JsonNode other = body(postAs(token, "/api/jobs/emails", Map.of("text",
                CONFIRMATION.replace("Graduate Software Engineer", "Data Analyst"))));

        assertThat(other.get("outcome").asText()).isEqualTo("CREATED");
        assertThat(other.get("title").asText()).isEqualTo("Canva – Data Analyst");
    }

    @Test
    void usesAnExistingJobHuntTracker() throws Exception {
        String token = newUserToken();
        String trackerId = body(postAs(token, "/api/trackers", Map.of("template", "job-hunt")))
                .at("/tracker/id").asText();

        JsonNode result = body(postAs(token, "/api/jobs/emails", Map.of("text", CONFIRMATION)));

        assertThat(result.get("trackerId").asText()).isEqualTo(trackerId);
    }

    @Test
    void everydayEmailsAreNotImported() throws Exception {
        String token = newUserToken();

        JsonNode result = body(postAs(token, "/api/jobs/emails", Map.of(
                "text", "Hi! Dinner at 7 on Saturday? Unfortunately I can't do Friday.")));

        assertThat(result.get("outcome").asText()).isEqualTo("NOT_A_JOB_EMAIL");
        assertThat(body(getAs(token, "/api/trackers"))).isEmpty();
    }

    @Test
    void applicationsArePrivateToEachPerson() throws Exception {
        String alice = newUserToken();
        String bob = newUserToken();
        String trackerId = body(postAs(alice, "/api/jobs/emails", Map.of("text", CONFIRMATION)))
                .get("trackerId").asText();

        getAs(bob, "/api/trackers/" + trackerId).andExpect(status().isNotFound());
        JsonNode bobs = body(postAs(bob, "/api/jobs/emails", Map.of("text", CONFIRMATION)));
        assertThat(bobs.get("outcome").asText()).isEqualTo("CREATED");
        assertThat(bobs.get("trackerId").asText()).isNotEqualTo(trackerId);
    }

    @Test
    void needsTextAndASignIn() throws Exception {
        String token = newUserToken();
        postAs(token, "/api/jobs/emails", Map.of("text", " ")).andExpect(status().isBadRequest());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/jobs/emails")
                        .contentType("application/json").content("{\"text\":\"hi\"}"))
                .andExpect(status().isUnauthorized());
    }
}
