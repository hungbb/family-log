package app.giaky.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.giaky.common.web.RateLimiter;
import app.giaky.identity.application.DeliveredTokenInspector;
import app.giaky.identity.domain.TokenPurpose;
import app.giaky.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * The walking skeleton of milestone M1, through real HTTP against a real PostgreSQL: register,
 * verify, sign in, reach a protected endpoint, refresh, sign out.
 *
 * <p>The unit tests already prove each step in isolation. This proves they compose — that Flyway's
 * constraints, the JPA mapping, the security chain and the services agree with one another, which
 * is the only thing an integration test is uniquely able to say.
 */
class AuthFlowIT extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private DeliveredTokenInspector tokens;
    @Autowired private RateLimiter rateLimiter;
    @Autowired private JdbcTemplate jdbcTemplate;

    private String email;

    @BeforeEach
    void setUp() {
        // A fresh address per test, so a rolled-back transaction cannot collide with the next one,
        // and the per-account login limit starts empty.
        email = "an-" + UUID.randomUUID() + "@example.com";
        // The per-address limits are in-memory and shared by the whole suite; without this the
        // sixth registering test of the run would be rate-limited rather than tested.
        rateLimiter.reset();
    }

    @Test
    @DisplayName("register, verify, sign in, read /me, refresh, sign out")
    void theWholeFlow() throws Exception {
        // Register.
        mockMvc.perform(
                        post("/api/v1/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(registerBody(email, "correct-horse-battery")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").isNotEmpty())
                .andExpect(jsonPath("$.status").value("PENDING_VERIFICATION"));

        // Verify, using the token the dev/test delivery adapter captured.
        String verifyToken = tokens.latestToken(email, TokenPurpose.EMAIL_VERIFY).orElseThrow();
        mockMvc.perform(
                        post("/api/v1/auth/verify-email")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json("token", verifyToken)))
                .andExpect(status().isNoContent());

        // Sign in.
        JsonNode session = signIn(email, "correct-horse-battery");
        assertThat(session.get("user").get("status").asText()).isEqualTo("ACTIVE");
        assertThat(session.get("user").get("emailVerified").asBoolean()).isTrue();
        assertThat(session.get("expiresIn").asLong()).isPositive();

        String accessToken = session.get("accessToken").asText();
        String refreshToken = session.get("refreshToken").asText();

        // Reach a protected endpoint.
        mockMvc.perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.locale").value("vi-VN"));

        // Refresh, and get a different refresh token back.
        MvcResult refreshed =
                mockMvc.perform(
                                post("/api/v1/auth/refresh")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(json("refreshToken", refreshToken)))
                        .andExpect(status().isOk())
                        .andReturn();
        String rotated = body(refreshed).get("refreshToken").asText();
        assertThat(rotated).isNotEqualTo(refreshToken);

        // Sign out.
        mockMvc.perform(
                        post("/api/v1/auth/logout")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json("refreshToken", rotated)))
                .andExpect(status().isNoContent());

        // And the session is really gone.
        mockMvc.perform(
                        post("/api/v1/auth/refresh")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json("refreshToken", rotated)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOKEN_INVALID"));
    }

    @Test
    @DisplayName("an unverified account can still sign in (phase doc §8)")
    void unverifiedAccountCanSignIn() throws Exception {
        register(email);

        assertThat(signIn(email, "correct-horse-battery").get("user").get("status").asText())
                .isEqualTo("PENDING_VERIFICATION");
    }

    @Test
    @DisplayName("registering the same address twice is 409 EMAIL_ALREADY_REGISTERED")
    void duplicateEmailIsRejected() throws Exception {
        register(email);

        mockMvc.perform(
                        post("/api/v1/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(registerBody(email.toUpperCase(java.util.Locale.ROOT), "another-good-password")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_REGISTERED"));
    }

    @Test
    @DisplayName("the address is stored normalized, so the unique index means what it says (I-U1)")
    void emailIsStoredNormalized() throws Exception {
        String shouty = "AN-" + UUID.randomUUID() + "@Example.COM";

        register(shouty);

        flushAndClear();
        Integer matches =
                jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM users WHERE email = ?",
                        Integer.class,
                        shouty.toLowerCase(java.util.Locale.ROOT));
        assertThat(matches).isEqualTo(1);
    }

    @Test
    @DisplayName("a password the policy refuses is 422 with the reason on the password field")
    void weakPasswordIsRejected() throws Exception {
        mockMvc.perform(
                        post("/api/v1/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(registerBody(email, "short")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("password"));
    }

    @Test
    @DisplayName("a wrong password and an unknown address produce byte-identical failures")
    void loginFailuresAreIndistinguishable() throws Exception {
        register(email);

        String wrongPassword =
                mockMvc.perform(
                                post("/api/v1/auth/login")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(loginBody(email, "not-the-password")))
                        .andExpect(status().isUnauthorized())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        String unknownAddress =
                mockMvc.perform(
                                post("/api/v1/auth/login")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                loginBody(
                                                        "nobody-" + UUID.randomUUID() + "@example.com",
                                                        "not-the-password")))
                        .andExpect(status().isUnauthorized())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();

        // Identical but for the correlation id, which is per-request by design.
        assertThat(stripCorrelationId(wrongPassword)).isEqualTo(stripCorrelationId(unknownAddress));
        assertThat(objectMapper.readTree(wrongPassword).get("code").asText()).isEqualTo("AUTH_REQUIRED");
    }

    @Test
    @DisplayName("forgot-password answers 202 whether or not the address has an account")
    void forgotPasswordNeverRevealsExistence() throws Exception {
        register(email);

        mockMvc.perform(
                        post("/api/v1/auth/forgot-password")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json("email", email)))
                .andExpect(status().isAccepted());
        mockMvc.perform(
                        post("/api/v1/auth/forgot-password")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json("email", "nobody-" + UUID.randomUUID() + "@example.com")))
                .andExpect(status().isAccepted());
    }

    @Test
    @DisplayName("a protected endpoint without a token is 401 AUTH_REQUIRED")
    void protectedEndpointsNeedAToken() throws Exception {
        mockMvc.perform(get("/api/v1/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
    }

    @Test
    @DisplayName("the dev token endpoint is absent under the test profile")
    void devEndpointIsNotRegistered() throws Exception {
        mockMvc.perform(get("/api/v1/dev/verification-tokens").param("email", email))
                .andExpect(status().isUnauthorized());
    }

    // --- helpers ---------------------------------------------------------------------------------

    private void register(String address) throws Exception {
        mockMvc.perform(
                        post("/api/v1/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(registerBody(address, "correct-horse-battery")))
                .andExpect(status().isCreated());
    }

    private JsonNode signIn(String address, String password) throws Exception {
        return body(
                mockMvc.perform(
                                post("/api/v1/auth/login")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(loginBody(address, password)))
                        .andExpect(status().isOk())
                        .andReturn());
    }

    private JsonNode body(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private String registerBody(String address, String password) throws Exception {
        return objectMapper.writeValueAsString(
                java.util.Map.of(
                        "email", address,
                        "password", password,
                        "displayName", "Nguyen Van An",
                        "locale", "vi-VN"));
    }

    private String loginBody(String address, String password) throws Exception {
        return objectMapper.writeValueAsString(
                java.util.Map.of("email", address, "password", password));
    }

    private String json(String field, String value) throws Exception {
        return objectMapper.writeValueAsString(java.util.Map.of(field, value));
    }

    private static String stripCorrelationId(String body) {
        return body.replaceAll("\"correlationId\":\"[^\"]*\"", "\"correlationId\":\"-\"");
    }
}
