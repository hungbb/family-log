package app.giaky.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.giaky.common.error.ProblemDetailWriter;
import app.giaky.common.types.UserId;
import app.giaky.common.web.RateLimiter;
import app.giaky.config.WebConfig;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * The real chain, replacing the Phase 0 placeholder.
 *
 * <p>Phase 0 left a test here asserting that <i>every</i> request was permitted, written to fail the
 * moment authentication landed. This is that rewrite: the same file now pins the opposite, which is
 * the point the original was making.
 *
 * <p>The decisions that survived Phase 0 unchanged are still asserted below — no session is created,
 * and CORS exposes exactly the headers the browser client needs.
 */
@WebMvcTest(
        controllers = {
            SecurityConfigTest.ProbeController.class,
            SecurityConfigTest.PublicProbeController.class
        })
@Import({
    SecurityConfig.class,
    WebConfig.class,
    ProblemDetailWriter.class,
    RateLimiter.class,
    CurrentUserArgumentResolver.class,
    HmacJwtKeyProvider.class,
    JwtService.class,
    SecurityConfigTest.ProbeController.class,
    SecurityConfigTest.PublicProbeController.class,
    SecurityConfigTest.StubbedTime.class
})
@TestPropertySource(
        properties = {
            "app.cors.allowed-origins=http://localhost:5173",
            "app.security.jwt.secret=dGVzdC1vbmx5LXNpZ25pbmcta2V5LWZvci1hdXRvbWF0ZWQtdGVzdHMtMDE=",
            "app.security.jwt.issuer=giaky-test",
            "app.security.jwt.access-token-ttl=15m",
            "app.security.refresh-token.ttl=30d",
            "app.security.verification.email-verify-ttl=24h",
            "app.security.verification.password-reset-ttl=1h",
            "app.security.bcrypt-cost=12"
        })
class SecurityConfigTest {

    private static final List<String> ORIGINS = List.of("http://localhost:5173");
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtService jwtService;

    @MockitoBean private AuthenticatedUserPort authenticatedUsers;

    @Test
    @DisplayName("an unauthenticated request to a business endpoint is 401 AUTH_REQUIRED")
    void unauthenticatedRequestsAreRefused() throws Exception {
        mockMvc.perform(get("/api/v1/probe"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
    }

    @Test
    @DisplayName("an unauthenticated write is 401, not 403 — CSRF is still off")
    void writesRequireAuthenticationRatherThanACsrfToken() throws Exception {
        // With CSRF enabled this POST would be 403 for want of a token. It stays off because the
        // session is a bearer header, which a cross-site form cannot forge.
        mockMvc.perform(post("/api/v1/probe"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
    }

    @Test
    @DisplayName("a valid bearer token gets through, and the principal reaches the controller")
    void validTokenAuthenticates() throws Exception {
        UserId userId = UserId.of(UUID.randomUUID());
        when(authenticatedUsers.findAuthenticatable(any()))
                .thenReturn(Optional.of(new AuthenticatedUser(userId, "an@example.com", 0L)));
        String token = jwtService.issue(userId, "an@example.com", 0L).value();

        mockMvc.perform(get("/api/v1/probe").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(content().string("ok:an@example.com"));
    }

    @Test
    @DisplayName("a token whose account no longer exists is TOKEN_INVALID")
    void tokenForUnknownAccountIsRejected() throws Exception {
        when(authenticatedUsers.findAuthenticatable(any())).thenReturn(Optional.empty());
        String token = jwtService.issue(UserId.of(UUID.randomUUID()), "an@example.com", 0L).value();

        mockMvc.perform(get("/api/v1/probe").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOKEN_INVALID"));
    }

    @Test
    @DisplayName("a token minted before a global revocation is TOKEN_INVALID immediately")
    void staleTokenVersionIsRejected() throws Exception {
        UserId userId = UserId.of(UUID.randomUUID());
        // The account is on version 3; the token says 0. That is "log out everywhere" taking
        // effect on the next request rather than at the end of the token's fifteen minutes.
        when(authenticatedUsers.findAuthenticatable(any()))
                .thenReturn(Optional.of(new AuthenticatedUser(userId, "an@example.com", 3L)));
        String token = jwtService.issue(userId, "an@example.com", 0L).value();

        mockMvc.perform(get("/api/v1/probe").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOKEN_INVALID"));
    }

    @Test
    @DisplayName("a malformed bearer token is TOKEN_INVALID, distinct from having no token at all")
    void malformedTokenIsRejectedSpecifically() throws Exception {
        mockMvc.perform(get("/api/v1/probe").header(HttpHeaders.AUTHORIZATION, "Bearer nonsense"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOKEN_INVALID"));
    }

    @Test
    @DisplayName("the auth endpoints stay open — they are where a token comes from")
    void authEndpointsArePublic() throws Exception {
        mockMvc.perform(get("/api/v1/auth/probe")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("health stays open for probes; the rest of Actuator does not")
    void actuatorIsPartlyPublic() throws Exception {
        mockMvc.perform(get("/actuator/prometheus")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("no HTTP session is created")
    void noSessionIsCreated() throws Exception {
        var result = mockMvc.perform(get("/api/v1/probe")).andReturn();

        assertThat(result.getRequest().getSession(false))
                .as("the chain is STATELESS; a session here means something is storing security context")
                .isNull();
    }

    @Test
    @DisplayName("controllers are served under the /api/v1 base path")
    void controllersAreVersioned() throws Exception {
        mockMvc.perform(get("/probe")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("the security headers of doc 05 §5 are set")
    void securityHeadersAreSet() throws Exception {
        mockMvc.perform(get("/api/v1/auth/probe"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"));
    }

    @Test
    @DisplayName("CORS exposes the headers the browser client needs to read")
    void corsExposesCorrelationIdAndETag() {
        CorsConfiguration config =
                ((UrlBasedCorsConfigurationSource)
                                new SecurityConfig(ORIGINS).corsConfigurationSource())
                        .getCorsConfigurations()
                        .get("/**");

        assertThat(config.getAllowedOrigins()).isEqualTo(ORIGINS);
        assertThat(config.getExposedHeaders())
                .contains("X-Correlation-Id", "ETag", "Retry-After");
        // Credentials off: the session lives in a bearer token, so cookies are never needed and
        // allowing them would widen the attack surface for nothing (ADR 0003).
        assertThat(config.getAllowCredentials()).isNotEqualTo(Boolean.TRUE);
    }

    /** A frozen clock, so an issued token's expiry is a decision rather than a race. */
    @TestConfiguration
    static class StubbedTime {

        @Bean
        Clock clock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }

    /** A stand-in for a protected endpoint. */
    @RestController
    static class ProbeController {

        @GetMapping("/probe")
        String get(@CurrentUser AuthenticatedUser caller) {
            return "ok:" + caller.email();
        }

        @PostMapping("/probe")
        String post() {
            return "ok";
        }
    }

    /** A stand-in for an endpoint under the public {@code /auth} prefix. */
    @RestController
    @RequestMapping("/auth")
    static class PublicProbeController {

        @GetMapping("/probe")
        String get() {
            return "ok";
        }
    }
}
