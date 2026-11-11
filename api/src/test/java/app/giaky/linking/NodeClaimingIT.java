package app.giaky.linking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.giaky.lineage.support.LineageApiSupport;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Claiming and linking end to end (FR-N1…FR-N6), over real HTTP and real PostgreSQL.
 *
 * <p>The unit tests already prove each rule against in-memory repositories. What this can say that
 * those cannot is that Flyway's constraints — {@code ux_claim_pending}, {@code
 * ux_persons_linked_user} — the JPA mapping, the security chain and the services all agree with one
 * another.
 *
 * <p>The fixture is the demo clan of the phase document: a founding ancestor, Chi Hai and Chi Ba,
 * the founder as clan administrator, and a branch-scoped approver for Chi Hai. Every scope
 * assertion below needs that shape to mean anything.
 */
class NodeClaimingIT extends LineageApiSupport {

    private Actor founder;
    private Actor member;
    private Actor chiHaiApprover;

    private UUID clanId;
    private String clanCode;
    private UUID rootBranch;
    private UUID chiHai;
    private UUID chiBa;

    private UUID ancestor;
    private UUID sonInChiHai;
    private UUID sonInChiBa;

    @BeforeEach
    void buildTheClan() throws Exception {
        founder = signUp("Nguyễn Văn Tổ");
        JsonNode clan = createClan(founder, "Nguyễn Văn");
        clanId = UUID.fromString(clan.get("id").asText());
        clanCode = clan.get("clanCode").asText();
        rootBranch = rootBranchId(clan);

        chiHai = UUID.fromString(createBranch(founder, clanId, rootBranch, "Chi Hai").get("id").asText());
        chiBa = UUID.fromString(createBranch(founder, clanId, rootBranch, "Chi Ba").get("id").asText());

        ancestor = foundingAncestor(founder, clanId, rootBranch, "Nguyễn Văn Ất");
        sonInChiHai = childOf(founder, clanId, ancestor, "Nguyễn Văn An", "MALE");
        sonInChiBa = childOf(founder, clanId, ancestor, "Nguyễn Văn Bảo", "MALE");
        moveToBranch(sonInChiHai, chiHai);
        moveToBranch(sonInChiBa, chiBa);

        member = signUp("Nguyễn Văn An");
        joinClan(member, founder, clanId, clanCode, chiHai);

        chiHaiApprover = signUp("Bác Hai");
        joinClan(chiHaiApprover, founder, clanId, clanCode, chiHai);
        grant(chiHaiApprover, "NODE_CLAIM_APPROVE", "BRANCH", chiHai);
    }

    @Test
    @DisplayName("claim, approve, and the requester's /me shows the linked node")
    void claimToLink() throws Exception {
        String claimId = claim(member, sonInChiHai, "Con của ông Ất");

        as(chiHaiApprover, post(claimPath(claimId) + "/approve"), Map.of())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        as(member, get("/api/v1/clans/" + clanId + "/persons/linked-to-me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.personId").value(sonInChiHai.toString()))
                .andExpect(jsonPath("$.self").value(true));

        as(member, get("/api/v1/me/claims"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("APPROVED"));
    }

    @Test
    @DisplayName("a second claim in the same clan is 409 USER_ALREADY_LINKED_IN_CLAN (I-P4)")
    void oneNodePerUserPerClan() throws Exception {
        String claimId = claim(member, sonInChiHai, null);
        as(chiHaiApprover, post(claimPath(claimId) + "/approve"), Map.of())
                .andExpect(status().isOk());

        as(member, post(claimsPath()), Map.of("personId", sonInChiBa.toString()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("USER_ALREADY_LINKED_IN_CLAN"));
    }

    @Test
    @DisplayName("a claim on an already-linked node is 409 NODE_ALREADY_LINKED (I-K1)")
    void nodeAlreadyLinked() throws Exception {
        Actor other = signUp("Người khác");
        joinClan(other, founder, clanId, clanCode, chiHai);
        as(founder, post(linkPath(sonInChiHai)), Map.of("userId", other.userId().toString()))
                .andExpect(status().isOk());

        as(member, post(claimsPath()), Map.of("personId", sonInChiHai.toString()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NODE_ALREADY_LINKED"));
    }

    @Test
    @DisplayName("a duplicate pending claim is 409, held by ux_claim_pending (I-K3)")
    void duplicatePendingClaim() throws Exception {
        claim(member, sonInChiHai, null);

        as(member, post(claimsPath()), Map.of("personId", sonInChiHai.toString()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_PENDING_REQUEST"));
    }

    @Test
    @DisplayName("an approver outside the node's scope is 403; one inside is 200")
    void scopeDecidesWhoMayApprove() throws Exception {
        Actor chiBaMember = signUp("Người Chi Ba");
        joinClan(chiBaMember, founder, clanId, clanCode, chiBa);
        String claimId = claim(chiBaMember, sonInChiBa, null);

        as(chiHaiApprover, post(claimPath(claimId) + "/approve"), Map.of())
                .andExpect(status().isForbidden());

        // The founder holds CLAN_ADMIN, which covers every chi (FR-Z6).
        as(founder, post(claimPath(claimId) + "/approve"), Map.of())
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("the inbox shows only what this approver may decide (FR-N2)")
    void inboxIsScopeFiltered() throws Exception {
        Actor chiBaMember = signUp("Người Chi Ba");
        joinClan(chiBaMember, founder, clanId, clanCode, chiBa);
        claim(member, sonInChiHai, null);
        claim(chiBaMember, sonInChiBa, null);

        as(chiHaiApprover, get(claimsPath() + "?status=PENDING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].personId").value(sonInChiHai.toString()))
                // The card an approver verifies a claim from (F6.5).
                .andExpect(jsonPath("$.content[0].parentNames[0]").value("Nguyễn Văn Ất"));
    }

    @Test
    @DisplayName("self-approval is refused unless the decider is a clan administrator (I-K4)")
    void noSelfApproval() throws Exception {
        grant(member, "NODE_CLAIM_APPROVE", "BRANCH", chiHai);
        String claimId = claim(member, sonInChiHai, null);

        as(member, post(claimPath(claimId) + "/approve"), Map.of())
                .andExpect(status().isForbidden());

        // The founder is the exception, and it is the bootstrap: the first member of a family is
        // the only person who could recognise themselves in it.
        String founderClaim = claim(founder, sonInChiBa, null);
        as(founder, post(claimPath(founderClaim) + "/approve"), Map.of())
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("approval never grants edit rights (doc 04 §6)")
    void approvalGrantsNothing() throws Exception {
        String claimId = claim(member, sonInChiHai, null);
        as(chiHaiApprover, post(claimPath(claimId) + "/approve"), Map.of())
                .andExpect(status().isOk());

        JsonNode permissions =
                body(
                        as(member, get("/api/v1/clans/" + clanId + "/my-permissions"))
                                .andExpect(status().isOk())
                                .andReturn());

        assertThat(permissions.get("permissions").toString()).doesNotContain("TREE_EDIT");
    }

    @Test
    @DisplayName("direct assignment of a non-member is 422 (FR-N4)")
    void directAssignmentOfNonMember() throws Exception {
        Actor stranger = signUp("Người lạ");

        as(founder, post(linkPath(sonInChiBa)), Map.of("userId", stranger.userId().toString()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("the member list says who already holds a node, and which (doc 05 §4.5)")
    void memberListCarriesTheLinkedNode() throws Exception {
        as(founder, post(linkPath(sonInChiHai)), Map.of("userId", member.userId().toString()))
                .andExpect(status().isOk());

        JsonNode members =
                body(
                        as(chiHaiApprover, get("/api/v1/clans/" + clanId + "/members"))
                                .andExpect(status().isOk())
                                .andReturn())
                        .get("content");

        assertThat(members).hasSize(3);
        for (JsonNode row : members) {
            if (row.get("userId").asText().equals(member.userId().toString())) {
                assertThat(row.get("linkedPersonId").asText()).isEqualTo(sonInChiHai.toString());
            } else {
                // The founder and the approver hold no node, and the field is absent rather than
                // somebody else's id.
                assertThat(row.hasNonNull("linkedPersonId")).isFalse();
            }
        }

        as(member, delete(linkPath(sonInChiHai))).andExpect(status().isNoContent());

        as(founder, get("/api/v1/clans/" + clanId + "/members"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.linkedPersonId)]").isEmpty());
    }

    @Test
    @DisplayName("unlink by the linked user themselves is 204 (FR-N5)")
    void selfUnlink() throws Exception {
        as(founder, post(linkPath(sonInChiHai)), Map.of("userId", member.userId().toString()))
                .andExpect(status().isOk());

        as(member, delete(linkPath(sonInChiHai))).andExpect(status().isNoContent());

        as(member, get("/api/v1/clans/" + clanId + "/persons/linked-to-me"))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("a removed member's node goes back to the unlinked pool, and they are told (I-P5)")
    void removalUnlinks() throws Exception {
        as(founder, post(linkPath(sonInChiHai)), Map.of("userId", member.userId().toString()))
                .andExpect(status().isOk());

        as(founder, delete("/api/v1/clans/" + clanId + "/members/" + member.userId()))
                .andExpect(status().isNoContent());

        as(founder, get(linkPath(sonInChiHai)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.linkedUserId").doesNotExist());
        JsonNode candidates =
                body(
                        as(founder, get("/api/v1/clans/" + clanId + "/persons/unlinked"))
                                .andExpect(status().isOk())
                                .andReturn());
        assertThat(candidates.get("content").toString()).contains(sonInChiHai.toString());

        // By type rather than by position: the test clock does not move, so the assignment's
        // notice and this one carry the same timestamp.
        as(member, get("/api/v1/me/notifications?unreadOnly=true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.type == 'NODE_UNLINKED')]", hasSize(1)));
    }

    @Test
    @DisplayName("somebody who leaves and is re-admitted comes back unlinked (I-P5)")
    void leavingUnlinks() throws Exception {
        as(founder, post(linkPath(sonInChiHai)), Map.of("userId", member.userId().toString()))
                .andExpect(status().isOk());

        as(member, post("/api/v1/clans/" + clanId + "/members/leave"))
                .andExpect(status().isNoContent());
        joinClan(member, founder, clanId, clanCode, chiHai);

        as(member, get("/api/v1/clans/" + clanId + "/persons/linked-to-me"))
                .andExpect(status().isNoContent());
        // Their own doing, so nothing to tell them: the only notice is the original assignment.
        as(member, get("/api/v1/me/notifications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.type == 'NODE_UNLINKED')]").isEmpty());
    }

    @Test
    @DisplayName("a membership ending withdraws the claims that member still had open (I-K2)")
    void membershipEndWithdrawsClaims() throws Exception {
        String claimId = claim(member, sonInChiHai, "Con của ông Ất");

        as(founder, delete("/api/v1/clans/" + clanId + "/members/" + member.userId()))
                .andExpect(status().isNoContent());

        as(member, get("/api/v1/me/claims"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(claimId))
                .andExpect(jsonPath("$[0].status").value("CANCELLED"));
        as(chiHaiApprover, get(claimsPath() + "?status=PENDING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
        // …and it is over: the approver is not left with a claim that can only be refused.
        as(chiHaiApprover, post(claimPath(claimId) + "/approve"), Map.of())
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("the unlinked browser excludes nodes that already have an account (B6.6)")
    void unlinkedBrowserExcludesLinked() throws Exception {
        as(founder, post(linkPath(sonInChiHai)), Map.of("userId", member.userId().toString()))
                .andExpect(status().isOk());

        JsonNode candidates =
                body(
                        as(member, get("/api/v1/clans/" + clanId + "/persons/unlinked"))
                                .andExpect(status().isOk())
                                .andReturn());

        assertThat(candidates.get("content").toString()).doesNotContain(sonInChiHai.toString());
        assertThat(candidates.get("content").toString()).contains(sonInChiBa.toString());
    }

    @Test
    @DisplayName("self-service edits the whitelisted fields, and 403s on somebody else's node")
    void selfServiceProfile() throws Exception {
        as(founder, post(linkPath(sonInChiHai)), Map.of("userId", member.userId().toString()))
                .andExpect(status().isOk());

        as(
                        member,
                        patch(selfProfilePath(sonInChiHai)),
                        Map.of("notesHtml", "<p>Sống ở Nam Định</p>"))
                .andExpect(status().isOk());

        as(member, get("/api/v1/clans/" + clanId + "/persons/" + sonInChiHai))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notesHtml").value("<p>Sống ở Nam Định</p>"));

        // The other half of the whitelist: a name is still TREE_EDIT, on their own node included.
        JsonNode person =
                body(
                        as(member, get("/api/v1/clans/" + clanId + "/persons/" + sonInChiHai))
                                .andExpect(status().isOk())
                                .andReturn());
        Map<String, Object> rename = new LinkedHashMap<>();
        rename.put("fullName", "Nguyễn Văn Khác");
        rename.put("version", person.get("version").asLong());
        as(member, patch("/api/v1/clans/" + clanId + "/persons/" + sonInChiHai), rename)
                .andExpect(status().isForbidden());

        // And somebody else's node is not theirs to touch through this door either.
        as(member, patch(selfProfilePath(sonInChiBa)), Map.of("notesHtml", "<p>hi</p>"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("a decision notifies the requester, and a claim notifies the approvers (B6.9)")
    void notifications() throws Exception {
        String claimId = claim(member, sonInChiHai, null);

        as(chiHaiApprover, get("/api/v1/me/notifications?unreadOnly=true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].type").value("CLAIM_SUBMITTED"));

        as(chiHaiApprover, post(claimPath(claimId) + "/approve"), Map.of())
                .andExpect(status().isOk());

        as(member, get("/api/v1/me/notifications?unreadOnly=true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].type").value("CLAIM_DECIDED"));
    }

    // --- helpers -------------------------------------------------------------------------------

    private String claimsPath() {
        return "/api/v1/clans/" + clanId + "/claims";
    }

    private String claimPath(String claimId) {
        return claimsPath() + "/" + claimId;
    }

    private String linkPath(UUID personId) {
        return "/api/v1/clans/" + clanId + "/persons/" + personId + "/link-user";
    }

    private String selfProfilePath(UUID personId) {
        return "/api/v1/clans/" + clanId + "/persons/" + personId + "/self-profile";
    }

    private String claim(Actor actor, UUID personId, String evidence) throws Exception {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("personId", personId.toString());
        if (evidence != null) {
            request.put("evidence", evidence);
        }
        return body(
                        as(actor, post(claimsPath()), request)
                                .andExpect(status().isCreated())
                                .andReturn())
                .get("id")
                .asText();
    }

    private void moveToBranch(UUID personId, UUID branchId) throws Exception {
        as(
                        founder,
                        post("/api/v1/clans/" + clanId + "/persons/" + personId + "/move-branch"),
                        Map.of("branchId", branchId.toString()))
                .andExpect(status().isOk());
    }

    private void grant(Actor grantee, String permission, String scopeType, UUID scopeRefId)
            throws Exception {

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("granteeUserId", grantee.userId().toString());
        request.put("permission", permission);
        request.put("scopeType", scopeType);
        if (scopeRefId != null) {
            request.put("scopeRefId", scopeRefId.toString());
        }
        as(founder, post("/api/v1/clans/" + clanId + "/permissions"), request)
                .andExpect(status().isCreated());
    }
}
