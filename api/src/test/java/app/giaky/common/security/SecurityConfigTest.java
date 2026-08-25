package app.giaky.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.giaky.config.WebConfig;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Pins the Phase 0 security placeholder.
 *
 * <p><b>{@link #everyRequestIsPermitted()} is designed to fail in Phase 1.</b> The chain currently
 * permits every request, which is only acceptable while there is nothing to protect and no way to
 * authenticate. When authentication lands, that test breaks — that is the point. Rewrite it against
 * the real rules; do not delete it, and do not relax {@code SecurityConfig} to keep it green.
 *
 * <p>The remaining assertions pin decisions that should survive Phase 1 unchanged: no session is
 * created, and CORS exposes exactly the headers the browser client needs to read.
 */
@WebMvcTest
@Import({SecurityConfig.class, WebConfig.class, SecurityConfigTest.ProbeController.class})
@TestPropertySource(properties = "app.cors.allowed-origins=http://localhost:5173")
class SecurityConfigTest {

    private static final List<String> ORIGINS = List.of("http://localhost:5173");

    @Autowired private MockMvc mockMvc;

    @Test
    @DisplayName("TODO(Phase 1): an unauthenticated request is still permitted")
    void everyRequestIsPermitted() throws Exception {
        mockMvc.perform(get("/api/v1/probe"))
                .andExpect(status().isOk())
                .andExpect(content().string("ok"));
    }

    @Test
    @DisplayName("TODO(Phase 1): an unauthenticated write is still permitted — CSRF is off")
    void writesAreNotCsrfProtected() throws Exception {
        // With CSRF enabled this POST would be rejected with 403 for want of a token. Phase 1 keeps
        // CSRF off (the client sends a bearer token, which a cross-site form cannot forge) but will
        // require authentication, so expect this to become a 401.
        mockMvc.perform(post("/api/v1/probe")).andExpect(status().isOk());
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
        mockMvc.perform(get("/probe")).andExpect(status().isNotFound());
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
        assertThat(config.getExposedHeaders()).contains("X-Correlation-Id", "ETag");
        // Credentials off: the session lives in a bearer token, so cookies are never needed and
        // allowing them would widen the attack surface for nothing.
        assertThat(config.getAllowCredentials()).isNotEqualTo(Boolean.TRUE);
    }

    /** A stand-in for the endpoints later phases add, so the chain has something to let through. */
    @RestController
    static class ProbeController {

        @GetMapping("/probe")
        String get() {
            return "ok";
        }

        @PostMapping("/probe")
        String post() {
            return "ok";
        }
    }
}
