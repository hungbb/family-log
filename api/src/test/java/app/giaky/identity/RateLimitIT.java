package app.giaky.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.giaky.common.web.RateLimitPolicy;
import app.giaky.common.web.RateLimiter;
import app.giaky.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The rate limits of doc 05 §5, exercised through the filter chain (task B1.12).
 *
 * <p>Each test resets the limiter first: the buckets are process-wide and shared with every other
 * integration test, so without a reset these would be measuring the rest of the suite.
 */
class RateLimitIT extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private RateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        rateLimiter.reset();
    }

    @Test
    @DisplayName("login is limited per address: the 11th attempt from one client is 429")
    void loginIsLimitedPerClientAddress() throws Exception {
        // Different accounts each time, so the per-account limit inside AuthService cannot be what
        // trips first — this test is about the filter.
        for (int attempt = 0; attempt < RateLimitPolicy.LOGIN_PER_IP.permits(); attempt++) {
            mockMvc.perform(
                            post("/api/v1/auth/login")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(loginBody(uniqueEmail())))
                    .andExpect(status().isUnauthorized());
        }

        mockMvc.perform(
                        post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(loginBody(uniqueEmail())))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"))
                .andExpect(header().exists("Retry-After"));
    }

    @Test
    @DisplayName("the same account is limited after five attempts, whatever address they come from")
    void loginIsLimitedPerAccount() throws Exception {
        String email = uniqueEmail();

        for (int attempt = 0; attempt < RateLimitPolicy.LOGIN_PER_ACCOUNT.permits(); attempt++) {
            mockMvc.perform(
                            post("/api/v1/auth/login")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(loginBody(email)))
                    .andExpect(status().isUnauthorized());
        }

        // The per-address limit still has room; this refusal is the per-account one, applied inside
        // AuthService because the filter cannot see the request body.
        mockMvc.perform(
                        post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(loginBody(email)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"));
    }

    @Test
    @DisplayName("registration is limited to five per minute per address")
    void registrationIsLimited() throws Exception {
        for (int attempt = 0; attempt < RateLimitPolicy.REGISTER_PER_IP.permits(); attempt++) {
            mockMvc.perform(
                            post("/api/v1/auth/register")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(registerBody(uniqueEmail())))
                    .andExpect(status().isCreated());
        }

        mockMvc.perform(
                        post("/api/v1/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(registerBody(uniqueEmail())))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"));
    }

    @Test
    @DisplayName("forgot-password is limited to five per minute per address")
    void forgotPasswordIsLimited() throws Exception {
        for (int attempt = 0; attempt < RateLimitPolicy.FORGOT_PASSWORD_PER_IP.permits(); attempt++) {
            mockMvc.perform(
                            post("/api/v1/auth/forgot-password")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(Map.of("email", uniqueEmail()))))
                    .andExpect(status().isAccepted());
        }

        mockMvc.perform(
                        post("/api/v1/auth/forgot-password")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(Map.of("email", uniqueEmail()))))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    @DisplayName("a rate-limited response is still Problem Details, with a usable Retry-After")
    void limitedResponseKeepsTheErrorContract() throws Exception {
        for (int attempt = 0; attempt <= RateLimitPolicy.REGISTER_PER_IP.permits(); attempt++) {
            mockMvc.perform(
                    post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(registerBody(uniqueEmail())));
        }

        var response =
                mockMvc.perform(
                                post("/api/v1/auth/register")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(registerBody(uniqueEmail())))
                        .andExpect(status().isTooManyRequests())
                        .andExpect(jsonPath("$.type").value("https://giaky.app/errors/rate-limited"))
                        .andExpect(jsonPath("$.status").value(429))
                        .andExpect(jsonPath("$.correlationId").isNotEmpty())
                        .andReturn()
                        .getResponse();

        assertThat(response.getContentType()).contains("application/problem+json");
        assertThat(Integer.parseInt(response.getHeader("Retry-After"))).isBetween(1, 60);
    }

    @Test
    @DisplayName("endpoints outside the three sensitive ones are not limited by this filter")
    void otherEndpointsAreNotLimited() throws Exception {
        for (int attempt = 0; attempt < 20; attempt++) {
            mockMvc.perform(
                            post("/api/v1/auth/verify-email")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(Map.of("token", "nope"))))
                    .andExpect(status().isUnauthorized());
        }
    }

    private static String uniqueEmail() {
        return "an-" + UUID.randomUUID() + "@example.com";
    }

    private String loginBody(String email) throws Exception {
        return objectMapper.writeValueAsString(
                Map.of("email", email, "password", "not-the-password"));
    }

    private String registerBody(String email) throws Exception {
        return objectMapper.writeValueAsString(
                Map.of(
                        "email", email,
                        "password", "correct-horse-battery",
                        "displayName", "Nguyen Van An"));
    }
}
