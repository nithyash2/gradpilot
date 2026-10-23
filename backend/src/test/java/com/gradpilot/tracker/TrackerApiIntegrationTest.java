package com.gradpilot.tracker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.gradpilot.ApiTestSupport;

class TrackerApiIntegrationTest extends ApiTestSupport {

    @Test
    void listsTemplates() throws Exception {
        String token = newUserToken();

        getAs(token, "/api/templates")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.key == 'job-hunt')].type").value("PIPELINE"))
                .andExpect(jsonPath("$[?(@.key == 'work-shifts')].type").value("SCHEDULE"));
    }

    @Test
    void createsTrackerFromTemplateWithStarterItems() throws Exception {
        String token = newUserToken();

        JsonNode created = body(postAs(token, "/api/trackers", Map.of("template", "driving-licence"))
                .andExpect(status().isCreated()));

        assertThat(created.at("/tracker/name").asText()).isEqualTo("Driving licence");
        assertThat(created.at("/tracker/type").asText()).isEqualTo("CHECKLIST");
        assertThat(created.get("items")).hasSize(4);
        assertThat(created.at("/tracker/stats/total").asInt()).isEqualTo(4);
        assertThat(created.at("/tracker/stats/done").asInt()).isZero();
    }

    @Test
    void createsCustomTrackerAndRejectsIncompleteRequest() throws Exception {
        String token = newUserToken();

        postAs(token, "/api/trackers", Map.of("name", "Visa paperwork", "type", "CHECKLIST"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tracker.name").value("Visa paperwork"));
        postAs(token, "/api/trackers", Map.of("name", "No type"))
                .andExpect(status().isBadRequest());
        postAs(token, "/api/trackers", Map.of("template", "not-a-template"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void pipelineItemsDefaultToFirstStageAndRejectUnknownStages() throws Exception {
        String token = newUserToken();
        String id = body(postAs(token, "/api/trackers", Map.of("template", "job-hunt"))).at("/tracker/id").asText();

        postAs(token, "/api/trackers/" + id + "/items", Map.of("title", "Heidi, AI Engineer"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.stage").value("To apply"));
        postAs(token, "/api/trackers/" + id + "/items", Map.of("title", "REA", "stage", "Interview"))
                .andExpect(status().isCreated());
        postAs(token, "/api/trackers/" + id + "/items", Map.of("title", "Bad", "stage", "Maybe"))
                .andExpect(status().isBadRequest());

        getAs(token, "/api/trackers")
                .andExpect(jsonPath("$[0].stats.byStage['To apply']").value(1))
                .andExpect(jsonPath("$[0].stats.byStage.Interview").value(1));
    }

    @Test
    void scheduleItemsNeedDateAndGetLeaveReminder() throws Exception {
        String token = newUserToken();
        String id = body(postAs(token, "/api/trackers",
                Map.of("template", "work-shifts", "settings", Map.of("travelMinutes", 60))))
                .at("/tracker/id").asText();

        postAs(token, "/api/trackers/" + id + "/items", Map.of("title", "Kitchen shift"))
                .andExpect(status().isBadRequest());

        String tomorrow = LocalDate.now().plusDays(2).toString();
        JsonNode shift = body(postAs(token, "/api/trackers/" + id + "/items", Map.of(
                "title", "Kitchen shift", "itemDate", tomorrow, "startTime", "16:00", "endTime", "22:00"))
                .andExpect(status().isCreated()));

        // 4pm Melbourne minus 60 minutes travel = 3pm Melbourne
        assertThat(shift.get("reminderAt").asText()).isNotBlank();
        java.time.ZonedDateTime reminder = java.time.Instant.parse(shift.get("reminderAt").asText())
                .atZone(java.time.ZoneId.of("Australia/Melbourne"));
        assertThat(reminder.toLocalTime()).isEqualTo(java.time.LocalTime.of(15, 0));
    }

    @Test
    void updatesAndDeletesItems() throws Exception {
        String token = newUserToken();
        JsonNode created = body(postAs(token, "/api/trackers", Map.of("template", "driving-licence")));
        String trackerId = created.at("/tracker/id").asText();
        String itemId = created.at("/items/0/id").asText();

        Map<String, Object> update = new HashMap<>();
        update.put("title", "Pass the Hazard Perception Test");
        update.put("done", true);
        putAs(token, "/api/trackers/" + trackerId + "/items/" + itemId, update)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.done").value(true));

        getAs(token, "/api/trackers/" + trackerId)
                .andExpect(jsonPath("$.tracker.stats.done").value(1));

        deleteAs(token, "/api/trackers/" + trackerId + "/items/" + itemId)
                .andExpect(status().isNoContent());
        getAs(token, "/api/trackers/" + trackerId)
                .andExpect(jsonPath("$.items.length()").value(3));
    }

    @Test
    void progressAndLogTrackersReportTotals() throws Exception {
        String token = newUserToken();
        String study = body(postAs(token, "/api/trackers", Map.of("template", "study-plan"))).at("/tracker/id").asText();
        postAs(token, "/api/trackers/" + study + "/items", Map.of("title", "PyTorch", "progress", 30));
        postAs(token, "/api/trackers/" + study + "/items", Map.of("title", "pandas", "progress", 70));
        postAs(token, "/api/trackers/" + study + "/items", Map.of("title", "Too far", "progress", 120))
                .andExpect(status().isBadRequest());

        String log = body(postAs(token, "/api/trackers", Map.of("template", "practice-log"))).at("/tracker/id").asText();
        postAs(token, "/api/trackers/" + log + "/items", Map.of("title", "Lesson", "amount", 60, "itemDate", "2026-10-01"));
        postAs(token, "/api/trackers/" + log + "/items", Map.of("title", "Lesson", "amount", 45, "itemDate", "2026-10-08"));

        getAs(token, "/api/trackers/" + study).andExpect(jsonPath("$.tracker.stats.averageProgress").value(50));
        getAs(token, "/api/trackers/" + log).andExpect(jsonPath("$.tracker.stats.totalAmount").value(105.0));
    }

    @Test
    void usersCannotSeeOrChangeEachOthersTrackers() throws Exception {
        String owner = newUserToken();
        String stranger = newUserToken();
        String id = body(postAs(owner, "/api/trackers", Map.of("template", "big-date"))).at("/tracker/id").asText();

        getAs(stranger, "/api/trackers/" + id).andExpect(status().isNotFound());
        postAs(stranger, "/api/trackers/" + id + "/items", Map.of("title", "x", "itemDate", "2026-12-12"))
                .andExpect(status().isNotFound());
        deleteAs(stranger, "/api/trackers/" + id).andExpect(status().isNotFound());
        getAs(stranger, "/api/trackers").andExpect(jsonPath("$.length()").value(0));

        deleteAs(owner, "/api/trackers/" + id).andExpect(status().isNoContent());
        getAs(owner, "/api/trackers/" + id).andExpect(status().isNotFound());
    }

    @Test
    void trackersRequireLogin() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/trackers"))
                .andExpect(status().isUnauthorized());
    }
}
