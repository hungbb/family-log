package app.giaky.authorization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.giaky.clanorg.support.ClanOrgApiSupport;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * <b>The isolation matrix of doc 04 §8, at the API layer.</b>
 *
 * <p>This is the test the product's central promise rests on (goal G5, roadmap risk R1): one
 * relative manages their own branch without touching anyone else's. Everything below goes through
 * real HTTP against real PostgreSQL, because what has to be true is not that the resolver returns
 * false — the unit tests already prove that in far more detail — but that no route reaches a
 * mutation without asking it.
 *
 * <p>The fixture is the clan doc 04 §8 describes: an đời-13 common ancestor at the root, and two chi
 * beneath it. Uncle C edits and manages roles in Chi Hai. Uncle D is simply a member of Chi Ba.
 *
 * <p>Every row is written out separately rather than parameterized. Phase 3 §8 asks for exactly
 * that: a table-driven version of this file would be shorter and would make a wrong expectation
 * much harder to see.
 */
class AuthorizationIsolationIT extends ClanOrgApiSupport {

    private Actor admin;
    private Actor uncleC;
    private Actor uncleD;
    private Actor newcomer;
    private Actor outsider;

    private UUID clanId;
    private String clanCode;
    private UUID rootBranch;
    private UUID chiHai;
    private UUID chiBa;

    @BeforeEach
    void aClanWithTwoChi() throws Exception {
        admin = signUp("Nguyễn Văn An");
        uncleC = signUp("Nguyễn Văn Cường");
        uncleD = signUp("Nguyễn Văn Dũng");
        newcomer = signUp("Nguyễn Thị Em");
        outsider = signUp("Người lạ");

        JsonNode clan = createClan(admin, "Nguyễn Văn");
        clanId = UUID.fromString(clan.get("id").asText());
        clanCode = clan.get("clanCode").asText();
        rootBranch = UUID.fromString(clan.get("rootBranchId").asText());
        chiHai = UUID.fromString(createBranch(admin, clanId, null, "Chi Hai").get("id").asText());
        chiBa = UUID.fromString(createBranch(admin, clanId, null, "Chi Ba").get("id").asText());

        joinClan(uncleC, admin, clanId, clanCode, chiHai);
        joinClan(uncleD, admin, clanId, clanCode, chiBa);
        joinClan(newcomer, admin, clanId, clanCode, chiHai);

        grant(admin, uncleC, "TREE_EDIT", "BRANCH", chiHai);
        grant(admin, uncleC, "ROLE_MANAGE", "BRANCH", chiHai);
    }

    // --- the founder's bootstrap ---------------------------------------------------------------

    @Test
    @DisplayName("the founder holds CLAN_ADMIN from the moment the clan exists (doc 04 §6)")
    void founderIsBootstrapped() throws Exception {
        as(admin, get(base() + "/my-permissions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permissions", org.hamcrest.Matchers.hasItem("CLAN_ADMIN")))
                .andExpect(jsonPath("$.grants[0].permission").value("CLAN_ADMIN"))
                .andExpect(jsonPath("$.grants[0].scopeType").value("CLAN"));
    }

    @Test
    @DisplayName("an approved member holds CLAN_VIEW at the branch they joined into")
    void joinerGetsScopedView() throws Exception {
        as(uncleD, get(base() + "/my-permissions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permissions").value("CLAN_VIEW"))
                .andExpect(jsonPath("$.grants[0].scopeType").value("BRANCH"))
                .andExpect(jsonPath("$.grants[0].scopeRefId").value(chiBa.toString()))
                .andExpect(jsonPath("$.grants[0].scopeLabel").value("Chi Ba"));
    }

    // --- the matrix ----------------------------------------------------------------------------

    @Test
    @DisplayName("Editor C → a node in Chi Hai: allowed")
    void editorCMayActInsideChiHai() throws Exception {
        // Persons arrive in Phase 4; a branch under Chi Hai is this phase's stand-in for "a node in
        // C" and exercises the same scope check the person endpoints will.
        as(
                        uncleC,
                        post(base() + "/branches"),
                        Map.of("name", "Nhánh trong Chi Hai", "parentBranchId", chiHai.toString()))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("Editor C → a node in Chi Ba: 403 OUT_OF_SCOPE")
    void editorCMayNotActInChiBa() throws Exception {
        as(
                        uncleC,
                        post(base() + "/branches"),
                        Map.of("name", "Nhánh trong Chi Ba", "parentBranchId", chiBa.toString()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("OUT_OF_SCOPE"))
                // Doc 04 §7: the refusal names what they do hold, so nobody has to open a ticket.
                .andExpect(jsonPath("$.detail", org.hamcrest.Matchers.containsString(chiHai.toString())));
    }

    @Test
    @DisplayName("Editor C → the đời-13 root branch: 403, ancestors are above the scope")
    void editorCMayNotActOnTheRoot() throws Exception {
        as(
                        uncleC,
                        post(base() + "/branches"),
                        Map.of("name", "Chi mới", "parentBranchId", rootBranch.toString()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("OUT_OF_SCOPE"));
    }

    @Test
    @DisplayName("Editor C → clan settings: 403, that needs CLAN_ADMIN")
    void editorCMayNotEditTheClan() throws Exception {
        JsonNode clan = body(as(admin, get(base())).andReturn());

        as(
                        uncleC,
                        patch(base()),
                        Map.of("name", "Họ Nguyễn", "version", clan.get("version").asLong()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
    }

    @Test
    @DisplayName("Editor C → grant TREE_EDIT@CLAN to themselves: 403 PERMISSION_ESCALATION_BLOCKED")
    void editorCMayNotEscalateToClanScope() throws Exception {
        as(
                        uncleC,
                        post(base() + "/permissions"),
                        grantBody(uncleC.userId(), "TREE_EDIT", "CLAN", null))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_ESCALATION_BLOCKED"));
    }

    @Test
    @DisplayName("Editor C → grant TREE_EDIT@Chi Ba: 403, the chi next door is not theirs")
    void editorCMayNotGrantIntoAnotherBranch() throws Exception {
        as(
                        uncleC,
                        post(base() + "/permissions"),
                        grantBody(uncleD.userId(), "TREE_EDIT", "BRANCH", chiBa))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_ESCALATION_BLOCKED"));
    }

    @Test
    @DisplayName("Editor C → grant CLAN_ADMIN: 403, only an administrator makes another")
    void editorCMayNotMintAdministrators() throws Exception {
        as(
                        uncleC,
                        post(base() + "/permissions"),
                        grantBody(newcomer.userId(), "CLAN_ADMIN", "CLAN", null))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_ESCALATION_BLOCKED"));
    }

    @Test
    @DisplayName("Editor C → grant MEMBER_APPROVE they do not hold: 403")
    void editorCMayNotGrantWhatTheyLack() throws Exception {
        as(
                        uncleC,
                        post(base() + "/permissions"),
                        grantBody(newcomer.userId(), "MEMBER_APPROVE", "BRANCH", chiHai))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_ESCALATION_BLOCKED"));
    }

    @Test
    @DisplayName("Editor C → grant TREE_EDIT@Chi Hai to another member: 201, they hold ROLE_MANAGE@C")
    void editorCMayGrantWithinTheirBranch() throws Exception {
        as(
                        uncleC,
                        post(base() + "/permissions"),
                        grantBody(newcomer.userId(), "TREE_EDIT", "BRANCH", chiHai))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.permission").value("TREE_EDIT"))
                .andExpect(jsonPath("$.scopeLabel").value("Chi Hai"))
                .andExpect(jsonPath("$.granteeName").value("Nguyễn Thị Em"))
                .andExpect(jsonPath("$.grantedByName").value("Nguyễn Văn Cường"));

        as(newcomer, get(base() + "/my-permissions"))
                .andExpect(jsonPath("$.permissions", org.hamcrest.Matchers.hasItem("TREE_EDIT")));
    }

    @Test
    @DisplayName("Admin → anything in the clan: allowed")
    void adminMayDoAnything() throws Exception {
        as(admin, post(base() + "/branches"), Map.of("name", "Chi Tư"))
                .andExpect(status().isCreated());
        as(admin, post(base() + "/permissions"), grantBody(uncleD.userId(), "TREE_EDIT", "CLAN", null))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("Non-member → anything: 404, never 403")
    void nonMemberSeesNothing() throws Exception {
        as(outsider, get(base() + "/permissions"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CLAN_NOT_FOUND"));
        as(outsider, post(base() + "/permissions"), grantBody(outsider.userId(), "CLAN_VIEW", "CLAN", null))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CLAN_NOT_FOUND"));
    }

    // --- the grant dialog's promise --------------------------------------------------------------

    @Test
    @DisplayName("grantable offers a branch role manager only their own branch")
    void grantableIsScoped() throws Exception {
        JsonNode offered = body(as(uncleC, get(base() + "/permissions/grantable")).andReturn());

        assertThat(offered.get("subtreeAvailable").asBoolean()).isFalse();
        for (JsonNode permission : offered.get("permissions")) {
            for (JsonNode scope : permission.get("scopes")) {
                assertThat(scope.get("scopeType").asText()).isEqualTo("BRANCH");
                assertThat(scope.get("scopeRefId").asText()).isEqualTo(chiHai.toString());
            }
        }
        assertThat(offered.get("permissions").findValuesAsText("permission"))
                .containsExactlyInAnyOrder("CLAN_VIEW", "TREE_EDIT", "ROLE_MANAGE");
    }

    // --- last administrator ----------------------------------------------------------------------

    @Test
    @DisplayName("the only administrator cannot revoke their own CLAN_ADMIN")
    void lastAdminCannotSelfDemote() throws Exception {
        JsonNode mine = body(as(admin, get(base() + "/my-permissions")).andReturn());
        String grantId = mine.get("grants").get(0).get("id").asText();

        as(admin, delete(base() + "/permissions/" + grantId))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("LAST_ADMIN_PROTECTED"));
    }

    @Test
    @DisplayName("the only administrator cannot leave or be removed")
    void lastAdminIsPinned() throws Exception {
        as(admin, post(base() + "/members/leave"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("LAST_ADMIN_PROTECTED"));
        as(admin, delete(base() + "/members/" + admin.userId()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("LAST_ADMIN_PROTECTED"));
    }

    @Test
    @DisplayName("with a second administrator, the first may step down")
    void secondAdminUnlocksTheFirst() throws Exception {
        as(admin, post(base() + "/permissions"), grantBody(uncleD.userId(), "CLAN_ADMIN", "CLAN", null))
                .andExpect(status().isCreated());

        JsonNode mine = body(as(admin, get(base() + "/my-permissions")).andReturn());
        String adminGrant =
                mine.get("grants").findParents("permission").stream()
                        .filter(node -> "CLAN_ADMIN".equals(node.get("permission").asText()))
                        .findFirst()
                        .orElseThrow()
                        .get("id")
                        .asText();

        as(admin, delete(base() + "/permissions/" + adminGrant)).andExpect(status().isNoContent());
        as(admin, get(base() + "/my-permissions"))
                .andExpect(
                        jsonPath(
                                "$.permissions",
                                org.hamcrest.Matchers.not(
                                        org.hamcrest.Matchers.hasItem("CLAN_ADMIN"))));
    }

    // --- membership and grants move together ------------------------------------------------------

    @Test
    @DisplayName("removing a member deactivates their grants (B3.12)")
    void removalDeactivatesGrants() throws Exception {
        as(admin, post(base() + "/permissions"), grantBody(uncleD.userId(), "TREE_EDIT", "BRANCH", chiBa))
                .andExpect(status().isCreated());

        as(admin, delete(base() + "/members/" + uncleD.userId())).andExpect(status().isNoContent());

        // They are out, so the clan is invisible to them again — and the grant row is revoked.
        as(uncleD, get(base() + "/my-permissions")).andExpect(status().isNotFound());
        JsonNode remaining = body(as(admin, get(base() + "/permissions")).andReturn());
        assertThat(remaining.findValuesAsText("granteeUserId"))
                .doesNotContain(uncleD.userId().toString());
    }

    @Test
    @DisplayName("subtree scope is live: a scope naming nobody is a 404, not a grant on nothing")
    void subtreeScopeIsResolved() throws Exception {
        // Phase 3 answered 422 SUBTREE_SCOPE_UNAVAILABLE here; Phase 4 turned the scope on (B4.8),
        // and what is left to refuse is a person this clan does not have.
        as(
                        admin,
                        post(base() + "/permissions"),
                        grantBody(uncleC.userId(), "TREE_EDIT", "SUBTREE", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PERSON_NOT_FOUND"));
    }

    // --- helpers ------------------------------------------------------------------------------

    private String base() {
        return "/api/v1/clans/" + clanId;
    }

    private void grant(Actor actor, Actor grantee, String permission, String scopeType, UUID scopeRef)
            throws Exception {

        as(actor, post(base() + "/permissions"), grantBody(grantee.userId(), permission, scopeType, scopeRef))
                .andExpect(status().isCreated());
    }

    /** A mutable map, because {@code Map.of} refuses the null {@code scopeRefId} of a clan scope. */
    private static Map<String, Object> grantBody(
            UUID granteeUserId, String permission, String scopeType, UUID scopeRefId) {

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("granteeUserId", granteeUserId.toString());
        body.put("permission", permission);
        body.put("scopeType", scopeType);
        if (scopeRefId != null) {
            body.put("scopeRefId", scopeRefId.toString());
        }
        return body;
    }
}
