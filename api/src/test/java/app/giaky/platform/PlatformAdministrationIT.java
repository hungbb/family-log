package app.giaky.platform;

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
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Super-admin administration over HTTP: who gets in, and what suspension does to a live session.
 *
 * <p>{@code application-test.yml} names {@code operator@giaky.test} as the super admin.
 */
class PlatformAdministrationIT extends AbstractIntegrationTest {

    private static final String PASSWORD = "correct-horse-battery";
    private static final String OPERATOR = "operator@giaky.test";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private RateLimiter rateLimiter;
    @Autowired private DeliveredTokenInspector tokens;
    @Autowired private JdbcTemplate jdbc;

    private String operatorToken;
    private String memberEmail;
    private String memberToken;
    private String memberId;

    @BeforeEach
    void setUp() throws Exception {
        rateLimiter.reset();
        register(OPERATOR);
        verify(OPERATOR);
        operatorToken = signIn(OPERATOR).get("accessToken").asText();

        memberEmail = "member-" + UUID.randomUUID() + "@example.com";
        register(memberEmail);
        JsonNode member = signIn(memberEmail);
        memberToken = member.get("accessToken").asText();
        memberId = member.get("user").get("id").asText();
    }

    @Test
    @DisplayName("/me tells the operator they are a super admin, and nobody else")
    void meCarriesTheFlag() throws Exception {
        mockMvc.perform(as(operatorToken, get("/api/v1/me")))
                .andExpect(jsonPath("$.superAdmin").value(true));
        mockMvc.perform(as(memberToken, get("/api/v1/me")))
                .andExpect(jsonPath("$.superAdmin").value(false));
    }

    @Test
    @DisplayName("a configured address that has not verified is not a super admin")
    void unverifiedOperatorIsRefused() throws Exception {
        flushAndClear();
        jdbc.update(
                "UPDATE users SET email_verified_at = NULL, status = 'PENDING_VERIFICATION'"
                        + " WHERE email = ?",
                OPERATOR);
        flushAndClear();

        mockMvc.perform(as(operatorToken, get("/api/v1/admin/users")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
    }

    @Test
    @DisplayName("every administration endpoint refuses an ordinary member")
    void membersAreRefused() throws Exception {
        for (MockHttpServletRequestBuilder request :
                new MockHttpServletRequestBuilder[] {
                    get("/api/v1/admin/users"),
                    post("/api/v1/admin/users/{id}/suspend", memberId),
                    post("/api/v1/admin/users/{id}/send-password-reset", memberId)
                }) {
            mockMvc.perform(as(memberToken, request))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
        }
    }

    @Test
    @DisplayName("the operator lists and searches every account")
    void listsUsers() throws Exception {
        mockMvc.perform(as(operatorToken, get("/api/v1/admin/users").param("search", memberEmail)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].email").value(memberEmail))
                .andExpect(jsonPath("$.content[0].status").value("PENDING_VERIFICATION"))
                .andExpect(jsonPath("$.content[0].superAdmin").value(false));
    }

    @Test
    @DisplayName("suspending a member ends their live session on its next request")
    void suspensionEndsTheSession() throws Exception {
        mockMvc.perform(as(memberToken, get("/api/v1/me"))).andExpect(status().isOk());

        mockMvc.perform(as(operatorToken, post("/api/v1/admin/users/{id}/suspend", memberId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUSPENDED"));

        mockMvc.perform(as(memberToken, get("/api/v1/me")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOKEN_INVALID"));
        flushAndClear();
        Integer live =
                jdbc.queryForObject(
                        "SELECT count(*) FROM refresh_tokens r JOIN users u ON u.id = r.user_id"
                                + " WHERE u.email = ? AND r.revoked_at IS NULL",
                        Integer.class,
                        memberEmail);
        assertThat(live).isZero();

        mockMvc.perform(as(operatorToken, post("/api/v1/admin/users/{id}/reactivate", memberId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_VERIFICATION"));
    }

    @Test
    @DisplayName("the operator cannot suspend themselves")
    void cannotSuspendSelf() throws Exception {
        String operatorId = signIn(OPERATOR).get("user").get("id").asText();

        mockMvc.perform(as(operatorToken, post("/api/v1/admin/users/{id}/suspend", operatorId)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SUPER_ADMIN_PROTECTED"));
    }

    @Test
    @DisplayName("marking an address verified activates the account")
    void marksVerified() throws Exception {
        mockMvc.perform(as(operatorToken, post("/api/v1/admin/users/{id}/verify-email", memberId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emailVerified").value(true))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    @DisplayName("a reset link sent by the operator reaches the member and lets them choose a password")
    void sendsPasswordReset() throws Exception {
        mockMvc.perform(
                        as(
                                operatorToken,
                                post("/api/v1/admin/users/{id}/send-password-reset", memberId)))
                .andExpect(status().isAccepted());

        String reset = tokens.latestToken(memberEmail, TokenPurpose.PASSWORD_RESET).orElseThrow();
        mockMvc.perform(
                        json(
                                post("/api/v1/auth/reset-password"),
                                Map.of("token", reset, "newPassword", "a-brand-new-passphrase")))
                .andExpect(status().isNoContent());
    }

    // --- helpers ---------------------------------------------------------------------------------

    private static MockHttpServletRequestBuilder as(
            String token, MockHttpServletRequestBuilder builder) {
        return builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    private MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder builder, Object body)
            throws Exception {
        return builder.contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body));
    }

    private void register(String email) throws Exception {
        mockMvc.perform(
                        json(
                                post("/api/v1/auth/register"),
                                Map.of(
                                        "email", email,
                                        "password", PASSWORD,
                                        "displayName", "Nguyen Van An")))
                .andExpect(status().isCreated());
    }

    private void verify(String email) throws Exception {
        String token = tokens.latestToken(email, TokenPurpose.EMAIL_VERIFY).orElseThrow();
        mockMvc.perform(json(post("/api/v1/auth/verify-email"), Map.of("token", token)))
                .andExpect(status().isNoContent());
    }

    private JsonNode signIn(String email) throws Exception {
        String body =
                mockMvc.perform(
                                json(
                                        post("/api/v1/auth/login"),
                                        Map.of("email", email, "password", PASSWORD)))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        return objectMapper.readTree(body);
    }
}
