package app.giaky.clanorg;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Doc 04 §7: <b>404 for a non-member, 403 for a member who lacks the permission.</b>
 *
 * <p>The distinction is deliberate and is worth a test of its own. A non-member must not be able to
 * tell an existing clan from an invented id, because a clan code is a shareable string and 403 would
 * turn the code space into something worth enumerating. A member already knows the clan exists, so a
 * precise 403 is simply the more useful answer.
 *
 * <p>Parameterized over every clan-scoped endpoint, for the same reason as {@code
 * TenancyBoundaryIT}: the endpoint that leaks is always the one nobody thought about.
 */
class ClanAccessIT extends ClanOrgApiSupport {

    private Actor founder;
    private Actor member;
    private Actor outsider;
    private UUID clanId;
    private UUID branchId;
    private UUID joinRequestId;

    @BeforeEach
    void oneClanAndThreeKindsOfCaller() throws Exception {
        founder = signUp("Nguyễn Văn An");
        member = signUp("Nguyễn Thị Bé");
        outsider = signUp("Người lạ");

        JsonNode clan = createClan(founder, "Nguyễn Văn");
        clanId = UUID.fromString(clan.get("id").asText());
        branchId = UUID.fromString(createBranch(founder, clanId, null, "Chi Hai").get("id").asText());
        joinClan(member, founder, clanId, clan.get("clanCode").asText(), null);

        Actor applicant = signUp("Người xin vào");
        joinRequestId =
                UUID.fromString(
                        body(
                                        as(
                                                        applicant,
                                                        post("/api/v1/join-requests"),
                                                        Map.of(
                                                                "clanCode",
                                                                clan.get("clanCode").asText()))
                                                .andReturn())
                                .get("id")
                                .asText());
    }

    /** Every clan-scoped endpoint this phase ships. */
    static Stream<Arguments> clanScopedEndpoints() {
        return Stream.of(
                Arguments.of("GET clan", "clan"),
                Arguments.of("PATCH clan", "clan-patch"),
                Arguments.of("DELETE clan", "clan-delete"),
                Arguments.of("GET branch tree", "branches"),
                Arguments.of("POST branch", "branch-create"),
                Arguments.of("GET branch", "branch"),
                Arguments.of("PATCH branch", "branch-patch"),
                Arguments.of("POST branch move", "branch-move"),
                Arguments.of("POST branch archive", "branch-archive"),
                Arguments.of("GET members", "members"),
                Arguments.of("DELETE member", "member-remove"),
                Arguments.of("POST leave", "leave"),
                Arguments.of("GET join-request inbox", "inbox"),
                Arguments.of("POST approve", "approve"),
                Arguments.of("POST reject", "reject"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("clanScopedEndpoints")
    @DisplayName("a non-member gets 404 CLAN_NOT_FOUND, never 403 (doc 04 §7)")
    void nonMemberGetsNotFound(String name, String endpoint) throws Exception {
        as(outsider, requestFor(endpoint))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CLAN_NOT_FOUND"));
    }

    @Test
    @DisplayName("a member gets 403 on the administrative endpoints, not 404")
    void memberGetsForbidden() throws Exception {
        // They already know the clan exists — they are in it — so hiding it would help nobody.
        as(member, requestFor("clan-patch"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
        as(member, requestFor("branch-create")).andExpect(status().isForbidden());
        as(member, requestFor("inbox")).andExpect(status().isForbidden());
        as(member, requestFor("member-remove")).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("a member can read everything a member is meant to read")
    void memberCanRead() throws Exception {
        as(member, requestFor("clan")).andExpect(status().isOk());
        as(member, requestFor("branches")).andExpect(status().isOk());
        as(member, requestFor("members")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("an unknown clan id and a clan the caller is not in are the same answer")
    void unknownAndForbiddenAreIndistinguishable() throws Exception {
        String invented =
                as(outsider, get("/api/v1/clans/" + UUID.randomUUID()))
                        .andExpect(status().isNotFound())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        String real =
                as(outsider, get("/api/v1/clans/" + clanId))
                        .andExpect(status().isNotFound())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();

        // Identical but for the instance path and the per-request correlation id.
        org.assertj.core.api.Assertions.assertThat(codeAndDetail(invented))
                .isEqualTo(codeAndDetail(real));
    }

    @Test
    @DisplayName("no token at all is 401, before any of this is reached")
    void anonymousIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/clans/" + clanId))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
    }

    private String codeAndDetail(String problemJson) throws Exception {
        JsonNode problem = objectMapper.readTree(problemJson);
        return problem.get("code").asText() + "|" + problem.get("detail").asText();
    }

    private MockHttpServletRequestBuilder requestFor(String endpoint) {
        String base = "/api/v1/clans/" + clanId;
        return switch (endpoint) {
            case "clan" -> get(base);
            case "clan-patch" -> jsonBody(patch(base), Map.of("name", "Đổi tên", "version", 0));
            case "clan-delete" -> delete(base);
            case "branches" -> get(base + "/branches");
            case "branch-create" -> jsonBody(post(base + "/branches"), Map.of("name", "Chi mới"));
            case "branch" -> get(base + "/branches/" + branchId);
            case "branch-patch" ->
                    jsonBody(patch(base + "/branches/" + branchId), Map.of("name", "x", "version", 0));
            case "branch-move" ->
                    jsonBody(
                            post(base + "/branches/" + branchId + "/move"),
                            Map.of("newParentBranchId", branchId.toString()));
            case "branch-archive" -> post(base + "/branches/" + branchId + "/archive");
            case "members" -> get(base + "/members");
            case "member-remove" -> delete(base + "/members/" + member.userId());
            case "leave" -> post(base + "/members/leave");
            case "inbox" -> get(base + "/join-requests");
            case "approve" -> jsonBody(post(base + "/join-requests/" + joinRequestId + "/approve"), Map.of());
            case "reject" ->
                    jsonBody(
                            post(base + "/join-requests/" + joinRequestId + "/reject"),
                            Map.of("reason", "no"));
            default -> throw new IllegalArgumentException("unknown endpoint: " + endpoint);
        };
    }

    private MockHttpServletRequestBuilder jsonBody(
            MockHttpServletRequestBuilder request, Object body) {
        try {
            return request.contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(body));
        } catch (Exception unserializable) {
            throw new IllegalStateException(unserializable);
        }
    }
}
