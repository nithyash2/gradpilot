package com.gradpilot.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gradpilot.TestcontainersConfiguration;
import com.gradpilot.user.UserRepository;

/**
 * Full sign-up, log-in and authenticated request flow against a real database.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AuthFlowIntegrationTest {

    private static final String PASSWORD = "correct-horse-battery";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private UserRepository users;

    private static String uniqueEmail() {
        return "grad-" + UUID.randomUUID() + "@example.com";
    }

    private MvcResult signup(String email, String password) throws Exception {
        String body = json.writeValueAsString(new SignupBody(email, password, "Nithya", "Australia/Melbourne"));
        return mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn();
    }

    private String tokenFrom(MvcResult result) throws Exception {
        JsonNode node = json.readTree(result.getResponse().getContentAsString());
        return node.get("token").asText();
    }

    @Test
    void signupCreatesAccountAndReturnsToken() throws Exception {
        String email = uniqueEmail();

        MvcResult result = signup(email, PASSWORD);

        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        JsonNode node = json.readTree(result.getResponse().getContentAsString());
        assertThat(node.get("token").asText()).isNotBlank();
        assertThat(node.get("user").get("email").asText()).isEqualTo(email);
        assertThat(node.get("user").has("passwordHash")).isFalse();

        String storedHash = users.findByEmailIgnoreCase(email).orElseThrow().getPasswordHash();
        assertThat(storedHash).isNotEqualTo(PASSWORD).startsWith("$2");
    }

    @Test
    void signupWithExistingEmailReturnsConflict() throws Exception {
        String email = uniqueEmail();
        signup(email, PASSWORD);

        MvcResult second = signup(email.toUpperCase(), PASSWORD);

        assertThat(second.getResponse().getStatus()).isEqualTo(409);
    }

    @Test
    void signupRejectsInvalidEmailAndShortPassword() throws Exception {
        String body = json.writeValueAsString(new SignupBody("not-an-email", "short", null, null));

        mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.email").exists())
                .andExpect(jsonPath("$.fields.password").exists());
    }

    @Test
    void signupRejectsUnknownTimezone() throws Exception {
        String body = json.writeValueAsString(new SignupBody(uniqueEmail(), PASSWORD, null, "Mars/Olympus"));

        mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void loginWithCorrectPasswordReturnsToken() throws Exception {
        String email = uniqueEmail();
        signup(email, PASSWORD);

        String body = json.writeValueAsString(new LoginBody(email, PASSWORD));
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.user.email").value(email));
    }

    @Test
    void loginWithWrongPasswordOrUnknownEmailReturnsSameError() throws Exception {
        String email = uniqueEmail();
        signup(email, PASSWORD);

        String wrongPassword = json.writeValueAsString(new LoginBody(email, "not-the-password"));
        String unknownEmail = json.writeValueAsString(new LoginBody(uniqueEmail(), PASSWORD));

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(wrongPassword))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Email or password is incorrect"));
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(unknownEmail))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Email or password is incorrect"));
    }

    @Test
    void meRequiresToken() throws Exception {
        mvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/me").header("Authorization", "Bearer not-a-real-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void meReturnsUserForValidToken() throws Exception {
        String email = uniqueEmail();
        String token = tokenFrom(signup(email, PASSWORD));

        mvc.perform(get("/api/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.displayName").value("Nithya"))
                .andExpect(jsonPath("$.timezone").value("Australia/Melbourne"));
    }

    @Test
    void healthStaysPublic() throws Exception {
        mvc.perform(get("/api/health")).andExpect(status().isOk());
    }

    private record SignupBody(String email, String password, String displayName, String timezone) {
    }

    private record LoginBody(String email, String password) {
    }
}
