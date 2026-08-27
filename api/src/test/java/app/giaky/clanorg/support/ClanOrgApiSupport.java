package app.giaky.clanorg.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import app.giaky.common.web.RateLimiter;
import app.giaky.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

/**
 * Shared plumbing for the ClanOrg integration tests: real HTTP, real PostgreSQL, real accounts.
 *
 * <p>The unit tests already prove each rule against in-memory repositories. What these can say that
 * those cannot is that Flyway's constraints, the JPA mapping, the security chain and the services
 * agree with one another — so everything here goes through the API rather than through a service.
 *
 * <p>Accounts are created by registering and signing in, not by inserting rows. A fixture that
 * fabricated a session would be testing a code path the product does not have.
 */
public abstract class ClanOrgApiSupport extends AbstractIntegrationTest {

    @Autowired protected MockMvc mockMvc;
    @Autowired protected ObjectMapper objectMapper;
    @Autowired protected RateLimiter rateLimiter;

    /**
     * A signed-in account.
     *
     * @param accessToken the bearer token; short-lived, but the suite runs on a frozen clock
     */
    public record Actor(UUID userId, String email, String displayName, String accessToken) {}

    @BeforeEach
    void resetRateLimits() {
        // The registration limit is per address and in-memory, shared by the whole suite. Without
        // this the sixth registering test of a run would be rate-limited rather than tested.
        rateLimiter.reset();
    }

    /** Registers an account and signs it in. Unverified accounts can sign in (phase 1 doc §8). */
    protected Actor signUp(String displayName) throws Exception {
        String email = "member-" + UUID.randomUUID() + "@example.com";
        String password = "correct-horse-battery";

        mockMvc.perform(
                        post("/api/v1/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(
                                                java.util.Map.of(
                                                        "email", email,
                                                        "password", password,
                                                        "displayName", displayName))))
                .andExpect(MockMvcResultMatchers.status().isCreated());

        MvcResult session =
                mockMvc.perform(
                                post("/api/v1/auth/login")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                objectMapper.writeValueAsString(
                                                        java.util.Map.of(
                                                                "email", email,
                                                                "password", password))))
                        .andExpect(MockMvcResultMatchers.status().isOk())
                        .andReturn();

        JsonNode body = body(session);
        return new Actor(
                UUID.fromString(body.get("user").get("id").asText()),
                email,
                displayName,
                body.get("accessToken").asText());
    }

    /** Performs a request as {@code actor}. */
    protected ResultActions as(Actor actor, MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(
                request.header(HttpHeaders.AUTHORIZATION, "Bearer " + actor.accessToken()));
    }

    /** Performs a request with a JSON body as {@code actor}. */
    protected ResultActions as(Actor actor, MockHttpServletRequestBuilder request, Object body)
            throws Exception {

        return mockMvc.perform(
                request.header(HttpHeaders.AUTHORIZATION, "Bearer " + actor.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)));
    }

    protected JsonNode body(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    /** Founds a clan and returns the response body. */
    protected JsonNode createClan(Actor founder, String name) throws Exception {
        return body(
                as(
                                founder,
                                post("/api/v1/clans"),
                                java.util.Map.of("name", name, "originPlace", "Nam Định"))
                        .andExpect(MockMvcResultMatchers.status().isCreated())
                        .andReturn());
    }

    /** Adds a branch and returns the response body. */
    protected JsonNode createBranch(Actor founder, UUID clanId, UUID parentBranchId, String name)
            throws Exception {

        java.util.Map<String, Object> request =
                parentBranchId == null
                        ? java.util.Map.of("name", name)
                        : java.util.Map.of("name", name, "parentBranchId", parentBranchId.toString());

        return body(
                as(founder, post("/api/v1/clans/" + clanId + "/branches"), request)
                        .andExpect(MockMvcResultMatchers.status().isCreated())
                        .andReturn());
    }

    /** Takes {@code joiner} through the whole join flow: ask by code, and be approved. */
    protected void joinClan(Actor joiner, Actor approver, UUID clanId, String clanCode, UUID homeBranchId)
            throws Exception {

        JsonNode request =
                body(
                        as(
                                        joiner,
                                        post("/api/v1/join-requests"),
                                        java.util.Map.of("clanCode", clanCode, "message", "Cháu trong họ"))
                                .andExpect(MockMvcResultMatchers.status().isCreated())
                                .andReturn());

        java.util.Map<String, Object> approval =
                homeBranchId == null
                        ? java.util.Map.of()
                        : java.util.Map.of("homeBranchId", homeBranchId.toString());

        as(
                        approver,
                        post(
                                "/api/v1/clans/"
                                        + clanId
                                        + "/join-requests/"
                                        + request.get("id").asText()
                                        + "/approve"),
                        approval)
                .andExpect(MockMvcResultMatchers.status().isOk());
    }
}
