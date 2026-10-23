package com.gradpilot.calendar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.gradpilot.ApiTestSupport;

class CalendarIntegrationTest extends ApiTestSupport {

    private final LocalDate tomorrow = LocalDate.now(ZoneId.of("Australia/Melbourne")).plusDays(1);

    @Test
    void feedHasShiftsWithALeaveAlarmCountdownsAndOpenTasks() throws Exception {
        String token = newUserToken();
        String shifts = trackerId(token, "work-shifts");
        postAs(token, "/api/trackers/" + shifts + "/items", Map.of(
                "title", "Kitchen shift", "itemDate", tomorrow.toString(), "startTime", "17:00", "endTime", "23:30"));
        String dates = trackerId(token, "big-date");
        postAs(token, "/api/trackers/" + dates + "/items", Map.of(
                "title", "Parents arrive, finally", "itemDate", tomorrow.plusDays(30).toString()));
        String jobs = trackerId(token, "job-hunt");
        postAs(token, "/api/trackers/" + jobs + "/items", Map.of(
                "title", "Canva – Graduate Engineer", "itemDate", tomorrow.toString()));

        JsonNode address = body(postAs(token, "/api/calendar", Map.of()).andExpect(status().isOk()));
        String url = address.get("url").asText();
        assertThat(url).startsWith("http://localhost/api/calendar/").endsWith(".ics");
        assertThat(address.get("webcal").asText()).startsWith("webcal://localhost/api/calendar/");

        String ics = mvc.perform(get(url.replace("http://localhost", "")))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/calendar;charset=UTF-8"))
                .andReturn().getResponse().getContentAsString();

        assertThat(ics).contains("SUMMARY:Kitchen shift", "TRIGGER:-PT45M",
                "SUMMARY:Parents arrive\\, finally", "DTSTART;VALUE=DATE:")
                .doesNotContain("Canva");
        // 17:00 in Melbourne is 06:00 or 07:00 UTC depending on daylight saving
        assertThat(ics).containsPattern("DTSTART:\\d{8}T0[67]0000Z");
    }

    @Test
    void addressCanBeShownAgainReplacedAndTurnedOff() throws Exception {
        String token = newUserToken();
        assertThat(body(getAs(token, "/api/calendar"))).isEmpty();

        String first = body(postAs(token, "/api/calendar", Map.of())).get("url").asText();
        assertThat(body(getAs(token, "/api/calendar")).get("url").asText()).isEqualTo(first);

        String second = body(postAs(token, "/api/calendar", Map.of())).get("url").asText();
        assertThat(second).isNotEqualTo(first);
        mvc.perform(get(first.replace("http://localhost", ""))).andExpect(status().isNotFound());
        mvc.perform(get(second.replace("http://localhost", ""))).andExpect(status().isOk());

        deleteAs(token, "/api/calendar").andExpect(status().isNoContent());
        mvc.perform(get(second.replace("http://localhost", ""))).andExpect(status().isNotFound());
    }

    @Test
    void unknownAddressesFindNothing() throws Exception {
        mvc.perform(get("/api/calendar/not-a-real-token-but-long-enough.ics")).andExpect(status().isNotFound());
        mvc.perform(get("/api/calendar/short.ics")).andExpect(status().isNotFound());
        mvc.perform(get("/api/calendar")).andExpect(status().isUnauthorized());
    }

    private String trackerId(String token, String template) throws Exception {
        return body(postAs(token, "/api/trackers", Map.of("template", template))).at("/tracker/id").asText();
    }
}
