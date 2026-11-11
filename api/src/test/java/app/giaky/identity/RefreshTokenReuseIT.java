package app.giaky.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.giaky.common.web.RateLimiter;
import app.giaky.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reuse detection, with every request in a transaction of its own — the way a deployment runs it.
 *
 * <p>{@code AuthSecurityIT} has had this scenario from the start and it passed while the product
 * was broken. That suite runs each test inside one rolled-back transaction, so the request that
 * detects the replay and the request that checks the result share it: the chain revocation was
 * rolled back by the rejection that followed it, and inside that single transaction nobody could
 * tell. Here nothing is shared, which is the only arrangement in which "revoke, then refuse" can be
 * seen to have kept the revocation.
 *
 * <p>Runs outside a transaction and so cleans up after itself, as {@code AbstractIntegrationTest}
 * requires. Deleting the account takes its tokens with it.
 */
@Transactional(propagation = Propagation.NEVER)
class RefreshTokenReuseIT extends AbstractIntegrationTest {

    private static final String PASSWORD = "correct-horse-battery";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private RateLimiter rateLimiter;
    @Autowired private JdbcTemplate jdbcTemplate;

    private String email;

    @BeforeEach
    void setUp() {
        email = "reuse-" + UUID.randomUUID() + "@example.com";
        rateLimiter.reset();
    }

    @AfterEach
    void removeTheAccount() {
        jdbcTemplate.update("DELETE FROM users WHERE email = ?", email);
    }

    @Test
    @DisplayName("replaying a rotated refresh token kills the chain — and it stays killed")
    void replayRevokesTheChainForGood() throws Exception {
        register();
        String stolen = signIn().get("refreshToken").asText();
        // The thief uses it first, so theirs is the live end of the chain.
        String thiefs = refresh(stolen).get("refreshToken").asText();

        // The owner's client presents the token it still holds. That is the replay.
        mockMvc.perform(
                        post("/api/v1/auth/refresh")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json("refreshToken", stolen)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOKEN_INVALID"));

        // In a new request, and so in a new transaction: the thief's token must be dead.
        mockMvc.perform(
                        post("/api/v1/auth/refresh")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json("refreshToken", thiefs)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOKEN_INVALID"));

        assertThat(tokensOfTheAccount("revoked_at IS NULL"))
                .as("no refresh token of that session is left alive")
                .isZero();
        assertThat(tokensOfTheAccount("revoked_reason = 'REUSE_DETECTED'"))
                .as("the chain was revoked for the reason the audit entry gives")
                .isPositive();
    }

    @Test
    @DisplayName("a second session of the same account is not caught in it")
    void otherSessionsSurvive() throws Exception {
        register();
        String first = signIn().get("refreshToken").asText();
        String otherDevice = signIn().get("refreshToken").asText();
        refresh(first);

        mockMvc.perform(
                        post("/api/v1/auth/refresh")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json("refreshToken", first)))
                .andExpect(status().isUnauthorized());

        assertThat(refresh(otherDevice).get("refreshToken").asText()).isNotBlank();
    }

    private int tokensOfTheAccount(String condition) {
        Integer count =
                jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM refresh_tokens t JOIN users u ON u.id = t.user_id"
                                + " WHERE u.email = ? AND "
                                + condition,
                        Integer.class,
                        email);
        return count == null ? 0 : count;
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
        return objectMapper.readTree(
                mockMvc.perform(
                                post("/api/v1/auth/login")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                objectMapper.writeValueAsString(
                                                        Map.of(
                                                                "email", email,
                                                                "password", PASSWORD))))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString());
    }

    private JsonNode refresh(String refreshToken) throws Exception {
        return objectMapper.readTree(
                mockMvc.perform(
                                post("/api/v1/auth/refresh")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(json("refreshToken", refreshToken)))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString());
    }

    private String json(String field, String value) throws Exception {
        return objectMapper.writeValueAsString(Map.of(field, value));
    }
}
