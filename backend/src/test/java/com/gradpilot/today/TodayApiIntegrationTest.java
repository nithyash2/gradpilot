package com.gradpilot.today;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.gradpilot.ApiTestSupport;

class TodayApiIntegrationTest extends ApiTestSupport {

    private static final ZoneId MELBOURNE = ZoneId.of("Australia/Melbourne");

    @Test
    void checkInIsEmptyUntilSavedThenUpdatesInPlace() throws Exception {
        String token = newUserToken();
        String day = "/api/checkins/2026-10-23";

        getAs(token, day).andExpect(status().isOk())
                .andExpect(jsonPath("$.saved").value(false))
                .andExpect(jsonPath("$.focusDone").value(false));

        Map<String, Object> checkin = new HashMap<>();
        checkin.put("wakeTime", "07:30");
        checkin.put("breakfast", "Idli and sambar");
        checkin.put("focus", "Apply to one graduate role");
        checkin.put("selfCare", true);
        putAs(token, day, checkin).andExpect(status().isOk())
                .andExpect(jsonPath("$.saved").value(true))
                .andExpect(jsonPath("$.wakeTime").value("07:30:00"));

        checkin.put("focusDone", true);
        putAs(token, day, checkin).andExpect(status().isOk());

        getAs(token, day)
                .andExpect(jsonPath("$.focus").value("Apply to one graduate role"))
                .andExpect(jsonPath("$.focusDone").value(true))
                .andExpect(jsonPath("$.selfCare").value(true));
        getAs(token, "/api/checkins?from=2026-10-20&to=2026-10-26")
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void checkInRangeIsLimited() throws Exception {
        String token = newUserToken();
        getAs(token, "/api/checkins?from=2026-01-01&to=2026-12-31").andExpect(status().isBadRequest());
        getAs(token, "/api/checkins?from=2026-10-10&to=2026-10-01").andExpect(status().isBadRequest());
    }

    @Test
    void checkInsArePrivate() throws Exception {
        String owner = newUserToken();
        String other = newUserToken();
        putAs(owner, "/api/checkins/2026-10-23", Map.of("focus", "Private plan"));

        getAs(other, "/api/checkins/2026-10-23").andExpect(jsonPath("$.saved").value(false));
    }

    @Test
    void profileUpdateChangesReminderTimesAndRejectsBadTimezone() throws Exception {
        String token = newUserToken();

        getAs(token, "/api/me")
                .andExpect(jsonPath("$.morningTime").value("07:10:00"))
                .andExpect(jsonPath("$.eveningTime").value("21:40:00"));

        putAs(token, "/api/me", Map.of("morningTime", "06:45", "displayName", "Nithya"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.morningTime").value("06:45:00"))
                .andExpect(jsonPath("$.eveningTime").value("21:40:00"))
                .andExpect(jsonPath("$.displayName").value("Nithya"));

        putAs(token, "/api/me", Map.of("timezone", "Nowhere/Place")).andExpect(status().isBadRequest());
    }

    @Test
    void todayShowsNextShiftDueItemsCountdownsAndCheckIn() throws Exception {
        String token = newUserToken();
        LocalDate today = LocalDate.now(MELBOURNE);

        String shifts = body(postAs(token, "/api/trackers",
                Map.of("template", "work-shifts", "settings", Map.of("travelMinutes", 30))))
                .at("/tracker/id").asText();
        postAs(token, "/api/trackers/" + shifts + "/items", Map.of(
                "title", "Kitchen shift", "itemDate", today.plusDays(1).toString(),
                "startTime", "16:00", "endTime", "22:00")).andExpect(status().isCreated());

        JsonNode licence = body(postAs(token, "/api/trackers", Map.of("template", "driving-licence")));
        String licenceId = licence.at("/tracker/id").asText();
        String firstStep = licence.at("/items/0/id").asText();
        putAs(token, "/api/trackers/" + licenceId + "/items/" + firstStep,
                Map.of("title", "Pass the Hazard Perception Test", "itemDate", today.toString()))
                .andExpect(status().isOk());

        String dates = body(postAs(token, "/api/trackers", Map.of("template", "big-date"))).at("/tracker/id").asText();
        postAs(token, "/api/trackers/" + dates + "/items",
                Map.of("title", "Parents arrive", "itemDate", today.plusDays(50).toString()));
        postAs(token, "/api/trackers/" + dates + "/items",
                Map.of("title", "Already happened", "itemDate", today.minusDays(3).toString()));

        putAs(token, "/api/checkins/" + today, Map.of("focus", "Book the driving test"));

        JsonNode body = body(getAs(token, "/api/today").andExpect(status().isOk()));

        assertThat(body.get("date").asText()).isEqualTo(today.toString());
        assertThat(body.at("/nextShift/shift/title").asText()).isEqualTo("Kitchen shift");
        assertThat(body.at("/nextShift/leaveAt").asText()).isEqualTo("15:30:00");
        assertThat(body.at("/nextShift/today").asBoolean()).isFalse();
        assertThat(body.get("dueToday")).hasSize(1);
        assertThat(body.at("/dueToday/0/trackerName").asText()).isEqualTo("Driving licence");
        assertThat(body.get("countdowns")).hasSize(1);
        assertThat(body.at("/countdowns/0/daysLeft").asLong()).isEqualTo(50);
        assertThat(body.at("/checkin/focus").asText()).isEqualTo("Book the driving test");
        assertThat(body.get("lastSevenDays")).hasSize(7);
    }

    @Test
    void todayWorksForBrandNewAccount() throws Exception {
        String token = newUserToken();

        getAs(token, "/api/today").andExpect(status().isOk())
                .andExpect(jsonPath("$.nextShift").doesNotExist())
                .andExpect(jsonPath("$.dueToday.length()").value(0))
                .andExpect(jsonPath("$.checkin.saved").value(false));
    }
}
