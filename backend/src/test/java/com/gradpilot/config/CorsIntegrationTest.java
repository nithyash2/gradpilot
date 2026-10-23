package com.gradpilot.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import com.gradpilot.ApiTestSupport;

@TestPropertySource(properties = "gradpilot.cors.allowed-origins=https://gradpilot.example.app")
class CorsIntegrationTest extends ApiTestSupport {

    @Test
    void allowsConfiguredWebAppOrigin() throws Exception {
        mvc.perform(options("/api/trackers")
                        .header("Origin", "https://gradpilot.example.app")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "Authorization, Content-Type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://gradpilot.example.app"));
    }

    @Test
    void rejectsOtherOrigins() throws Exception {
        mvc.perform(options("/api/trackers")
                        .header("Origin", "https://someone-else.example.com")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden());
    }
}
