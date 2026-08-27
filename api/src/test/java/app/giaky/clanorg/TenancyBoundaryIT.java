package app.giaky.clanorg;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.giaky.clanorg.support.ClanOrgApiSupport;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * NFR-4, the tenancy boundary: <b>an id from clan B, used inside clan A's URL, is a 404.</b>
 *
 * <p>Parameterized over every endpoint that takes a nested id, because tenant isolation is only as
 * good as its weakest query and the weakest one is always the endpoint nobody thought about. Doc 09
 * §4.5 names this test as one of the six that exist from the phase that introduces them and are
 * never deleted.
 *
 * <p>404 rather than 403 throughout (doc 04 §7): the caller is an administrator of <i>their own</i>
 * clan, and telling them "forbidden" would confirm that the id they guessed belongs to something.
 */
class TenancyBoundaryIT extends ClanOrgApiSupport {

    private Actor owner;
    private UUID myClan;
    private UUID myBranch;
    private UUID theirClan;
    private UUID theirBranch;
    private UUID theirJoinRequest;
    private UUID theirMember;

    @BeforeEach
    void twoClans() throws Exception {
        // Clan A, whose administrator will do the probing.
        owner = signUp("Nguyễn Văn An");
        JsonNode mine = createClan(owner, "Nguyễn Văn");
        myClan = UUID.fromString(mine.get("id").asText());
        myBranch = UUID.fromString(createBranch(owner, myClan, null, "Chi Hai").get("id").asText());

        // Clan B, which A has nothing to do with.
        Actor stranger = signUp("Trần Thị Bình");
        JsonNode theirs = createClan(stranger, "Trần Gia");
        theirClan = UUID.fromString(theirs.get("id").asText());
        theirBranch =
                UUID.fromString(
                        createBranch(stranger, theirClan, null, "Chi của họ").get("id").asText());
        theirMember = stranger.userId();

        Actor applicant = signUp("Người xin vào");
        theirJoinRequest =
                UUID.fromString(
                        body(
                                        as(
                                                        applicant,
                                                        post("/api/v1/join-requests"),
                                                        Map.of(
                                                                "clanCode",
                                                                theirs.get("clanCode").asText()))
                                                .andReturn())
                                .get("id")
                                .asText());
    }

    /**
     * The endpoints that take a nested id, each built with an id from the other clan.
     *
     * <p>Adding an endpoint that takes one and forgetting to add it here is the gap this test cannot
     * close by itself; the checklist is in the phase document's exit criteria.
     */
    static Stream<Arguments> substitutions() {
        return Stream.of(
                Arguments.of("GET branch detail", "branch"),
                Arguments.of("PATCH branch", "branch-patch"),
                Arguments.of("POST branch move (subject)", "branch-move-subject"),
                Arguments.of("POST branch move (destination)", "branch-move-destination"),
                Arguments.of("POST branch archive", "branch-archive"),
                Arguments.of("POST branch create under a foreign parent", "branch-create"),
                Arguments.of("GET members filtered by a foreign branch", "members-branch-filter"),
                Arguments.of("DELETE a foreign clan's member", "member-remove"),
                Arguments.of("POST approve a foreign clan's join request", "join-approve"),
                Arguments.of("POST reject a foreign clan's join request", "join-reject"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("substitutions")
    @DisplayName("an id belonging to another clan is 404 inside my clan's URL")
    void foreignIdsAreNotFound(String name, String endpoint) throws Exception {
        as(owner, requestFor(endpoint))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("and the same request with my own ids succeeds, so the 404s mean something")
    void ownIdsStillWork() throws Exception {
        // Without this, a test suite in which every endpoint was simply broken would pass.
        as(owner, get("/api/v1/clans/" + myClan + "/branches/" + myBranch))
                .andExpect(status().isOk());
        as(owner, get("/api/v1/clans/" + myClan + "/members").param("branchId", myBranch.toString()))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("a foreign clan id in the path is a 404, whichever id follows it")
    void foreignClanIdIsNotFound() throws Exception {
        as(owner, get("/api/v1/clans/" + theirClan)).andExpect(status().isNotFound());
        as(owner, get("/api/v1/clans/" + theirClan + "/branches/" + theirBranch))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("a member-list filter naming a foreign branch reveals nothing about it")
    void foreignBranchFilterIsNotFound() throws Exception {
        as(owner, get("/api/v1/clans/" + myClan + "/members").param("branchId", theirBranch.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("BRANCH_NOT_FOUND"));
    }

    private MockHttpServletRequestBuilder requestFor(String endpoint) {
        String base = "/api/v1/clans/" + myClan;
        return switch (endpoint) {
            case "branch" -> get(base + "/branches/" + theirBranch);
            case "branch-patch" ->
                    jsonBody(patch(base + "/branches/" + theirBranch), Map.of("name", "x", "version", 0));
            case "branch-move-subject" ->
                    jsonBody(
                            post(base + "/branches/" + theirBranch + "/move"),
                            Map.of("newParentBranchId", myBranch.toString()));
            case "branch-move-destination" ->
                    jsonBody(
                            post(base + "/branches/" + myBranch + "/move"),
                            Map.of("newParentBranchId", theirBranch.toString()));
            case "branch-archive" -> post(base + "/branches/" + theirBranch + "/archive");
            case "branch-create" ->
                    jsonBody(
                            post(base + "/branches"),
                            Map.of("name", "Chi mới", "parentBranchId", theirBranch.toString()));
            case "members-branch-filter" ->
                    get(base + "/members").param("branchId", theirBranch.toString());
            case "member-remove" -> org.springframework.test.web.servlet.request
                    .MockMvcRequestBuilders.delete(base + "/members/" + theirMember);
            case "join-approve" ->
                    jsonBody(post(base + "/join-requests/" + theirJoinRequest + "/approve"), Map.of());
            case "join-reject" ->
                    jsonBody(
                            post(base + "/join-requests/" + theirJoinRequest + "/reject"),
                            Map.of("reason", "no"));
            default -> throw new IllegalArgumentException("unknown endpoint: " + endpoint);
        };
    }

    private MockHttpServletRequestBuilder jsonBody(
            MockHttpServletRequestBuilder request, Object body) {
        try {
            return request.contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(body));
        } catch (Exception unserializable) {
            throw new IllegalStateException(unserializable);
        }
    }
}
