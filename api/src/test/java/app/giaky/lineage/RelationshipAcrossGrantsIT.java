package app.giaky.lineage;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.giaky.lineage.support.LineageApiSupport;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * <b>An editor of two chi may relate people across them</b> (doc 04 §1.3 and §5.2).
 *
 * <p>The editor here holds {@code TREE_EDIT} on Chi Hai and, separately, on Chi Ba — two grants,
 * which is how somebody ends up looking after two branches. Every card in both carries {@code
 * canEdit: true}, each person can be edited on their own, and recording a marriage between one of
 * each was answered {@code 403 OUT_OF_SCOPE} — "You hold TREE_EDIT on BRANCH(hai), BRANCH(ba), which
 * does not cover 2 linked people" — because the resolver looked for one grant spanning the edge.
 *
 * <p>§5.2 asks for the permission on <i>each</i> endpoint. This asserts that, and that the guard the
 * rule exists for is intact: an edge into a chi the editor holds nothing on is still refused.
 */
class RelationshipAcrossGrantsIT extends LineageApiSupport {

    private Actor admin;
    private Actor editor;
    private UUID clanId;

    private UUID manInHai;
    private UUID womanInBa;
    private UUID childInBa;
    private UUID womanInTu;

    @BeforeEach
    void twoGrantsOneEditor() throws Exception {
        admin = signUp("Nguyễn Văn An");
        editor = signUp("Nguyễn Văn Cường");

        JsonNode clan = createClan(admin, "Nguyễn Văn");
        clanId = UUID.fromString(clan.get("id").asText());
        UUID rootBranch = rootBranchId(clan);
        UUID chiHai = branch(rootBranch, "Chi Hai");
        UUID chiBa = branch(rootBranch, "Chi Ba");
        UUID chiTu = branch(rootBranch, "Chi Tư");

        UUID ancestor = foundingAncestor(admin, clanId, rootBranch, "Nguyễn Phúc Tổ");
        manInHai = placed(childOf(admin, clanId, ancestor, "Nguyễn Văn Hai", "MALE"), chiHai);
        womanInBa = placed(unplaced("Trần Thị Ba", "FEMALE"), chiBa);
        childInBa = placed(unplaced("Nguyễn Văn Con", "MALE"), chiBa);
        womanInTu = placed(unplaced("Lê Thị Tư", "FEMALE"), chiTu);

        joinClan(editor, admin, clanId, clan.get("clanCode").asText(), chiHai);
        grantEdit(chiHai);
        grantEdit(chiBa);
    }

    @Test
    @DisplayName("the tree marks people of both chi editable, and nobody in the third")
    void canEditFollowsTheUnionOfGrants() throws Exception {
        as(editor, get("/api/v1/clans/" + clanId + "/tree"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.persons[?(@.id == '" + manInHai + "')].canEdit").value(true))
                .andExpect(jsonPath("$.persons[?(@.id == '" + womanInBa + "')].canEdit").value(true))
                .andExpect(
                        jsonPath("$.persons[?(@.id == '" + womanInTu + "')].canEdit").value(false));
    }

    @Test
    @DisplayName("a marriage between one person of each chi is allowed")
    void marriageAcrossTwoGrants() throws Exception {
        as(
                        editor,
                        post("/api/v1/clans/" + clanId + "/unions"),
                        Map.of(
                                "partnerAId", manInHai.toString(),
                                "partnerBId", womanInBa.toString(),
                                "unionType", "PRIMARY"))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("so is a parent in one chi with a child in the other")
    void parentageAcrossTwoGrants() throws Exception {
        as(
                        editor,
                        post("/api/v1/clans/" + clanId + "/parentages"),
                        Map.of(
                                "parentPersonId", manInHai.toString(),
                                "childPersonId", childInBa.toString(),
                                "parentRole", "FATHER"))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("an edge into a chi they hold nothing on is still 403 OUT_OF_SCOPE")
    void edgeIntoAThirdChiIsRefused() throws Exception {
        as(
                        editor,
                        post("/api/v1/clans/" + clanId + "/unions"),
                        Map.of(
                                "partnerAId", manInHai.toString(),
                                "partnerBId", womanInTu.toString(),
                                "unionType", "PRIMARY"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("OUT_OF_SCOPE"));
    }

    // --- helpers -------------------------------------------------------------------------------

    private UUID branch(UUID parent, String name) throws Exception {
        return UUID.fromString(createBranch(admin, clanId, parent, name).get("id").asText());
    }

    private UUID unplaced(String name, String gender) throws Exception {
        return UUID.fromString(
                createPerson(
                                admin,
                                clanId,
                                name,
                                Map.of(
                                        "gender", gender,
                                        "placement", placement("UNPLACED", null, null)))
                        .get("id")
                        .asText());
    }

    private UUID placed(UUID personId, UUID branchId) throws Exception {
        as(
                        admin,
                        post("/api/v1/clans/" + clanId + "/persons/" + personId + "/move-branch"),
                        Map.of("branchId", branchId.toString()))
                .andExpect(status().isOk());
        return personId;
    }

    private void grantEdit(UUID branchId) throws Exception {
        as(
                        admin,
                        post("/api/v1/clans/" + clanId + "/permissions"),
                        Map.of(
                                "granteeUserId", editor.userId().toString(),
                                "permission", "TREE_EDIT",
                                "scopeType", "BRANCH",
                                "scopeRefId", branchId.toString()))
                .andExpect(status().isCreated());
    }
}
