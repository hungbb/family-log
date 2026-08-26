package app.giaky.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.giaky.common.security.HmacJwtKeyProvider;
import app.giaky.common.security.JwtService;
import app.giaky.common.security.SecurityProperties;
import app.giaky.common.types.UserId;
import app.giaky.common.web.RateLimiter;
import app.giaky.identity.application.DeliveredTokenInspector;
import app.giaky.identity.domain.TokenPurpose;
import app.giaky.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Date;
import java.util.Locale;
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

/**
 * The abuse cases for Phase 1 (phase doc §5, "Security cases").
 *
 * <p>Every test here is something an attacker would try. They run against the real chain and the
 * real database, because the interesting failures are the ones where a component behaves correctly
 * on its own and the composition does not.
 */
class AuthSecurityIT extends AbstractIntegrationTest {

    private static final String PASSWORD = "correct-horse-battery";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private DeliveredTokenInspector tokens;
    @Autowired private RateLimiter rateLimiter;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private SecurityProperties securityProperties;
    @Autowired private Clock clock;

    private String email;

    @BeforeEach
    void setUp() {
        email = "an-" + UUID.randomUUID() + "@example.com";
        rateLimiter.reset();
    }

    @Test
    @DisplayName("an expired access token is 401 TOKEN_EXPIRED, not a generic refusal")
    void expiredAccessTokenIsDistinguishable() throws Exception {
        // The client's whole refresh strategy turns on telling this apart from TOKEN_INVALID
        // (doc 07 §4.2), so the distinction is worth an explicit test.
        UserId user = UserId.of(UUID.fromString(register(email)));
        Instant longAgo = clock.instant().minus(Duration.ofHours(2));
        String stale =
                new JwtService(
                                new HmacJwtKeyProvider(securityProperties),
                                Clock.fixed(longAgo, ZoneOffset.UTC),
                                securityProperties)
                        .issue(user, email, 0L)
                        .value();

        mockMvc.perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + stale))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOKEN_EXPIRED"));
    }

    @Test
    @DisplayName("a JWT with an altered payload is rejected")
    void tamperedTokenIsRejected() throws Exception {
        register(email);
        String[] parts = signIn(email).get("accessToken").asText().split("\\.");
        String forgedPayload =
                Base64.getUrlEncoder()
                        .withoutPadding()
                        .encodeToString(
                                ("{\"iss\":\"giaky-test\",\"sub\":\""
                                                + UUID.randomUUID()
                                                + "\",\"email\":\"attacker@example.com\","
                                                + "\"tokenVersion\":0,\"exp\":99999999999}")
                                        .getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(
                        get("/api/v1/me")
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        "Bearer " + parts[0] + '.' + forgedPayload + '.' + parts[2]))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOKEN_INVALID"));
    }

    @Test
    @DisplayName("an unsigned token — alg: none — is rejected")
    void unsignedTokenIsRejected() throws Exception {
        String userId = register(email);
        String unsigned =
                Jwts.builder()
                        .issuer(securityProperties.jwt().issuer())
                        .subject(userId)
                        .claim("email", email)
                        .claim("tokenVersion", 0L)
                        .expiration(Date.from(clock.instant().plus(Duration.ofHours(1))))
                        .compact();

        mockMvc.perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + unsigned))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOKEN_INVALID"));
    }

    @Test
    @DisplayName("a token for a deactivated account stops working immediately (I-U2)")
    void tokenForDeactivatedAccountIsRejected() throws Exception {
        String userId = register(email);
        String accessToken = signIn(email).get("accessToken").asText();
        mockMvc.perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk());

        flushAndClear();
        jdbcTemplate.update(
                "UPDATE users SET status = 'DEACTIVATED' WHERE id = ?", UUID.fromString(userId));
        flushAndClear();

        // No waiting for the token to expire: the filter re-reads the account on every request.
        mockMvc.perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOKEN_INVALID"));
    }

    @Test
    @DisplayName("reusing a rotated refresh token revokes the whole chain")
    void refreshTokenReuseRevokesTheChain() throws Exception {
        register(email);
        String first = signIn(email).get("refreshToken").asText();
        String second = refresh(first).get("refreshToken").asText();
        String third = refresh(second).get("refreshToken").asText();

        // The thief replays the token they captured two rotations ago.
        mockMvc.perform(
                        post("/api/v1/auth/refresh")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json("refreshToken", first)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOKEN_INVALID"));

        // And the real client's live token dies with it.
        mockMvc.perform(
                        post("/api/v1/auth/refresh")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json("refreshToken", third)))
                .andExpect(status().isUnauthorized());

        flushAndClear();
        Integer live =
                jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM refresh_tokens WHERE revoked_at IS NULL", Integer.class);
        assertThat(live).isZero();
        Integer reuseDetected =
                jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM refresh_tokens WHERE revoked_reason = 'REUSE_DETECTED'",
                        Integer.class);
        assertThat(reuseDetected).isPositive();
    }

    @Test
    @DisplayName("a password reset invalidates every session, refresh and access alike")
    void passwordResetEndsEverySession() throws Exception {
        register(email);
        JsonNode session = signIn(email);
        String accessToken = session.get("accessToken").asText();
        String refreshToken = session.get("refreshToken").asText();

        mockMvc.perform(
                        post("/api/v1/auth/forgot-password")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json("email", email)))
                .andExpect(status().isAccepted());
        String resetToken = tokens.latestToken(email, TokenPurpose.PASSWORD_RESET).orElseThrow();

        mockMvc.perform(
                        post("/api/v1/auth/reset-password")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(
                                                Map.of(
                                                        "token", resetToken,
                                                        "newPassword", "a-brand-new-passphrase"))))
                .andExpect(status().isNoContent());

        // The refresh token is revoked...
        mockMvc.perform(
                        post("/api/v1/auth/refresh")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json("refreshToken", refreshToken)))
                .andExpect(status().isUnauthorized());
        // ...and so is the access token, because tokenVersion moved on. Without that bump the
        // intruder this reset was meant to evict would keep working for another fifteen minutes.
        mockMvc.perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOKEN_INVALID"));
    }

    @Test
    @DisplayName("a reset token is single-use")
    void resetTokenIsSingleUse() throws Exception {
        register(email);
        mockMvc.perform(
                        post("/api/v1/auth/forgot-password")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json("email", email)))
                .andExpect(status().isAccepted());
        String resetToken = tokens.latestToken(email, TokenPurpose.PASSWORD_RESET).orElseThrow();
        String body =
                objectMapper.writeValueAsString(
                        Map.of("token", resetToken, "newPassword", "a-brand-new-passphrase"));

        mockMvc.perform(
                        post("/api/v1/auth/reset-password")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .andExpect(status().isNoContent());
        mockMvc.perform(
                        post("/api/v1/auth/reset-password")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("a verification token cannot be spent as a password reset")
    void verificationTokenCannotResetAPassword() throws Exception {
        register(email);
        String verifyToken = tokens.latestToken(email, TokenPurpose.EMAIL_VERIFY).orElseThrow();

        mockMvc.perform(
                        post("/api/v1/auth/reset-password")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(
                                                Map.of(
                                                        "token", verifyToken,
                                                        "newPassword", "a-brand-new-passphrase"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("no response body anywhere in the auth flow contains the stored hash")
    void noResponseLeaksThePasswordHash() throws Exception {
        String userId = register(email);
        flushAndClear();
        String storedHash =
                jdbcTemplate.queryForObject(
                        "SELECT password_hash FROM users WHERE id = ?",
                        String.class,
                        UUID.fromString(userId));
        JsonNode session = signIn(email);

        String profile =
                mockMvc.perform(
                                get("/api/v1/me")
                                        .header(
                                                HttpHeaders.AUTHORIZATION,
                                                "Bearer " + session.get("accessToken").asText()))
                        .andReturn()
                        .getResponse()
                        .getContentAsString();

        assertThat(session.toString()).doesNotContain(storedHash).doesNotContain("passwordHash");
        assertThat(profile).doesNotContain(storedHash).doesNotContain("passwordHash");
    }

    @Test
    @DisplayName("the stored refresh token is a hash, so a leaked table is not a set of sessions")
    void refreshTokensAreStoredHashed() throws Exception {
        register(email);
        String refreshToken = signIn(email).get("refreshToken").asText();

        flushAndClear();
        Integer stored =
                jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM refresh_tokens WHERE token_hash = ?",
                        Integer.class,
                        refreshToken);

        assertThat(stored).isZero();
    }

    @Test
    @DisplayName("logging out everywhere ends the other browser's session on its next request")
    void logoutAllEndsOtherSessions() throws Exception {
        register(email);
        JsonNode firstBrowser = signIn(email);
        JsonNode secondBrowser = signIn(email);

        mockMvc.perform(
                        post("/api/v1/auth/logout-all")
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        "Bearer " + secondBrowser.get("accessToken").asText()))
                .andExpect(status().isNoContent());

        mockMvc.perform(
                        get("/api/v1/me")
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        "Bearer " + firstBrowser.get("accessToken").asText()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(
                        post("/api/v1/auth/refresh")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json("refreshToken", firstBrowser.get("refreshToken").asText())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("logout-all without a token is 401 rather than a silent no-op")
    void logoutAllNeedsACaller() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout-all"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
    }

    // --- helpers ---------------------------------------------------------------------------------

    /** @return the new account's id */
    private String register(String address) throws Exception {
        String body =
                mockMvc.perform(
                                post("/api/v1/auth/register")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                objectMapper.writeValueAsString(
                                                        Map.of(
                                                                "email", address,
                                                                "password", PASSWORD,
                                                                "displayName", "Nguyen Van An"))))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        return objectMapper.readTree(body).get("userId").asText();
    }

    private JsonNode signIn(String address) throws Exception {
        String body =
                mockMvc.perform(
                                post("/api/v1/auth/login")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                objectMapper.writeValueAsString(
                                                        Map.of(
                                                                "email",
                                                                address.toLowerCase(Locale.ROOT),
                                                                "password",
                                                                PASSWORD))))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        return objectMapper.readTree(body);
    }

    private JsonNode refresh(String refreshToken) throws Exception {
        String body =
                mockMvc.perform(
                                post("/api/v1/auth/refresh")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(json("refreshToken", refreshToken)))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        return objectMapper.readTree(body);
    }

    private String json(String field, String value) throws Exception {
        return objectMapper.writeValueAsString(Map.of(field, value));
    }
}
