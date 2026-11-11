package app.giaky.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.giaky.common.web.RateLimiter;
import app.giaky.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** The {@code /me} endpoints of doc 05 §4.2 (FR-A4). */
class AccountIT extends AbstractIntegrationTest {

    private static final String PASSWORD = "correct-horse-battery";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private RateLimiter rateLimiter;

    private String email;
    private String accessToken;

    @BeforeEach
    void setUp() throws Exception {
        rateLimiter.reset();
        email = "an-" + UUID.randomUUID() + "@example.com";
        register();
        accessToken = signIn().get("accessToken").asText();
    }

    @Test
    @DisplayName("GET /me returns the signed-in account and nothing about anyone else")
    void readsTheProfile() throws Exception {
        mockMvc.perform(authorized(get("/api/v1/me")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.displayName").value("Nguyen Van An"))
                .andExpect(jsonPath("$.locale").value("vi-VN"))
                .andExpect(jsonPath("$.status").value("PENDING_VERIFICATION"))
                .andExpect(jsonPath("$.emailVerified").value(false))
                .andExpect(jsonPath("$.createdAt").isNotEmpty());
    }

    @Test
    @DisplayName("PATCH /me updates the given fields and returns what was stored")
    void updatesTheProfile() throws Exception {
        mockMvc.perform(
                        authorized(patch("/api/v1/me"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(
                                                Map.of("displayName", "An Nguyen", "locale", "en-US"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("An Nguyen"))
                .andExpect(jsonPath("$.locale").value("en-US"));

        mockMvc.perform(authorized(get("/api/v1/me")))
                .andExpect(jsonPath("$.displayName").value("An Nguyen"))
                .andExpect(jsonPath("$.locale").value("en-US"));
    }

    @Test
    @DisplayName("an absent field in a PATCH is left alone")
    void partialUpdateLeavesOtherFields() throws Exception {
        mockMvc.perform(
                        authorized(patch("/api/v1/me"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(Map.of("locale", "en-US"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Nguyen Van An"))
                .andExpect(jsonPath("$.locale").value("en-US"));
    }

    @Test
    @DisplayName("an unsupported locale is a 422 with the field named, not a 500")
    void unknownLocaleIsRejected() throws Exception {
        mockMvc.perform(
                        authorized(patch("/api/v1/me"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(Map.of("locale", "fr-FR"))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("locale"));
    }

    @Test
    @DisplayName("a blank display name is refused — empty, or made of whitespace")
    void blankDisplayNameIsRejected() throws Exception {
        // "   " used to get past @Size(min = 1) and be refused by the aggregate instead, which the
        // client saw as 500 INTERNAL_ERROR.
        for (String blank : new String[] {"", "   ", "\u3000"}) {
            mockMvc.perform(
                            authorized(patch("/api/v1/me"))
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(
                                            objectMapper.writeValueAsString(
                                                    Map.of("displayName", blank))))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.errors[0].field").value("displayName"));
        }
    }

    @Test
    @DisplayName("GET /me/sessions lists the caller's live sessions, with no token in sight")
    void listsSessions() throws Exception {
        signIn();

        String body =
                mockMvc.perform(authorized(get("/api/v1/me/sessions")))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$", Matchers.hasSize(2)))
                        .andExpect(jsonPath("$[0].id").isNotEmpty())
                        .andExpect(jsonPath("$[0].issuedAt").isNotEmpty())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();

        assertThat(body).doesNotContain("token").doesNotContain("hash");
    }

    @Test
    @DisplayName("the session list empties after logging out everywhere")
    void sessionsGoAwayOnLogoutAll() throws Exception {
        mockMvc.perform(authorized(post("/api/v1/auth/logout-all"))).andExpect(status().isNoContent());

        // The caller's own access token is now stale too, so a fresh sign-in is needed to look.
        String fresh = signIn().get("accessToken").asText();
        mockMvc.perform(get("/api/v1/me/sessions").header(HttpHeaders.AUTHORIZATION, "Bearer " + fresh))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", Matchers.hasSize(1)));
    }

    @Test
    @DisplayName("/me needs a token")
    void meRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/me")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/me/sessions")).andExpect(status().isUnauthorized());
        mockMvc.perform(
                        patch("/api/v1/me")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"displayName\":\"x\"}"))
                .andExpect(status().isUnauthorized());
    }

    // --- helpers ---------------------------------------------------------------------------------

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder authorized(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder builder) {
        return builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken);
    }

    private void register() throws Exception {
        mockMvc.perform(
                        post("/api/v1/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(
                                                Map.of(
                                                        "email", email,
                                                        "password", PASSWORD,
                                                        "displayName", "Nguyen Van An"))))
                .andExpect(status().isCreated());
    }

    private JsonNode signIn() throws Exception {
        String body =
                mockMvc.perform(
                                post("/api/v1/auth/login")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                objectMapper.writeValueAsString(
                                                        Map.of("email", email, "password", PASSWORD))))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        return objectMapper.readTree(body);
    }
}
