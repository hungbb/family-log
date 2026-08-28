package app.giaky.lineage;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
 * <b>{@code SUBTREE} scope, live</b> (B4.8, doc 04 §3.1, phase 4 §5).
 *
 * <p>Phase 3 built the resolver's subtree rules and could not test them against anything: there were
 * no people, so {@code GrantService} refused the scope outright. This is the test that was waiting
 * for the tree to exist, and it is the isolation matrix of doc 04 §8 asked one scope narrower.
 *
 * <p>The shape it asserts is precisely the one doc 04 §3 describes: a subtree grant reaches the
 * person it names and everybody below them, and reaches <b>nothing else</b> — not their parent, not
 * their siblings, not their spouse's family. A leak in any of those directions is a leak in goal G5.
 */
class SubtreeIsolationIT extends LineageApiSupport {

    private Actor admin;
    private Actor uncleC;
    private UUID clanId;

    private UUID doi1;
    private UUID uncle;
    private UUID uncleChild;
    private UUID uncleGrandchild;
    private UUID sibling;
    private UUID spouseOfUncle;

    @BeforeEach
    void buildTheFamily() throws Exception {
        admin = signUp("Nguyễn Văn An");
        uncleC = signUp("Nguyễn Văn Cường");

        JsonNode clan = createClan(admin, "Nguyễn Văn");
        clanId = UUID.fromString(clan.get("id").asText());
        UUID rootBranch = rootBranchId(clan);
        String clanCode = clan.get("clanCode").asText();

        // đời 1 → đời 2 → uncle C and his brother → C's line below him.
        doi1 = foundingAncestor(admin, clanId, rootBranch, "Nguyễn Phúc Tổ");
        UUID doi2 = childOf(admin, clanId, doi1, "Nguyễn Văn Hai", "MALE");
        uncle = childOf(admin, clanId, doi2, "Nguyễn Văn Cường", "MALE");
        sibling = childOf(admin, clanId, doi2, "Nguyễn Văn Dũng", "MALE");
        uncleChild = childOf(admin, clanId, uncle, "Nguyễn Văn Em", "MALE");
        uncleGrandchild = childOf(admin, clanId, uncleChild, "Nguyễn Văn Út", "MALE");
        spouseOfUncle = spouseOf(admin, clanId, uncle, "Trần Thị Hoa", "FEMALE");

        joinClan(uncleC, admin, clanId, clanCode, null);

        // The grant this whole phase was building towards.
        as(
                        admin,
                        post("/api/v1/clans/" + clanId + "/permissions"),
                        Map.of(
                                "granteeUserId", uncleC.userId().toString(),
                                "permission", "TREE_EDIT",
                                "scopeType", "SUBTREE",
                                "scopeRefId", uncle.toString()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.scopeType").value("SUBTREE"))
                .andExpect(jsonPath("$.scopeLabel").value("Nguyễn Văn Cường"));
    }

    @Test
    @DisplayName("the holder may edit the person the grant names — the self-row at depth 0")
    void mayEditThemselves() throws Exception {
        rename(uncleC, uncle, "Nguyễn Văn Cường (sửa)").andExpect(status().isOk());
    }

    @Test
    @DisplayName("and every descendant, however deep")
    void mayEditEveryDescendant() throws Exception {
        rename(uncleC, uncleChild, "Nguyễn Văn Em (sửa)").andExpect(status().isOk());
        rename(uncleC, uncleGrandchild, "Nguyễn Văn Út (sửa)").andExpect(status().isOk());
    }

    @Test
    @DisplayName("and may add new descendants, which join the subtree immediately")
    void mayAddDescendants() throws Exception {
        UUID added = childOf(uncleC, clanId, uncleGrandchild, "Nguyễn Văn Mới", "MALE");

        // The closure was extended inside the same transaction, so the new node is already inside
        // the grant — no second request, no window where it is not.
        rename(uncleC, added, "Nguyễn Văn Mới (sửa)").andExpect(status().isOk());
    }

    @Test
    @DisplayName("but not their own parent — ancestors are above the scope")
    void mayNotEditTheirParent() throws Exception {
        JsonNode parent =
                body(
                        as(admin, get("/api/v1/clans/" + clanId + "/persons/" + uncle))
                                .andReturn());
        UUID father = UUID.fromString(parent.get("parents").get(0).get("person").get("id").asText());

        rename(uncleC, father, "Đổi tên bố").andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("nor their siblings — a sibling line is not below them")
    void mayNotEditSiblings() throws Exception {
        rename(uncleC, sibling, "Đổi tên anh").andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("nor the đời-1 ancestor")
    void mayNotEditTheFoundingAncestor() throws Exception {
        rename(uncleC, doi1, "Đổi tên tổ").andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("nor their spouse, who married in rather than descended")
    void mayNotEditTheirSpouse() throws Exception {
        // Doc 04 §3: a subtree excludes "spouses' own ancestors", and a spouse is not a descendant
        // of the person they married. Editing them is a change to the family they came from.
        rename(uncleC, spouseOfUncle, "Đổi tên vợ").andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("doc 04 §5.2: linking a node inside the scope to a parent outside it is refused")
    void mayNotLinkOutOfScope() throws Exception {
        UUID insideTheScope = childOf(admin, clanId, uncleChild, "Nguyễn Văn Trong", "MALE");

        as(
                        uncleC,
                        post("/api/v1/clans/" + clanId + "/parentages"),
                        Map.of(
                                "parentPersonId", sibling.toString(),
                                "childPersonId", insideTheScope.toString(),
                                "parentRole", "MOTHER"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("reads stay clan-wide: the holder still sees the whole tree (doc 04 §5.5)")
    void readsAreNotPartitioned() throws Exception {
        as(uncleC, get("/api/v1/clans/" + clanId + "/tree"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.persons.length()").value(7));
    }

    @Test
    @DisplayName("canEdit marks exactly the subtree, and nothing else")
    void canEditMarksTheSubtree() throws Exception {
        JsonNode tree = body(as(uncleC, get("/api/v1/clans/" + clanId + "/tree")).andReturn());

        long editable = 0;
        for (JsonNode person : tree.get("persons")) {
            if (person.get("canEdit").asBoolean()) {
                editable++;
            }
        }

        // The uncle, his child, his grandchild. Not his wife, not his brother, not his father.
        org.assertj.core.api.Assertions.assertThat(editable).isEqualTo(3);
    }

    @Test
    @DisplayName("a subtree grant naming somebody who does not exist is a 404")
    void grantMustNameSomebodyReal() throws Exception {
        as(
                        admin,
                        post("/api/v1/clans/" + clanId + "/permissions"),
                        Map.of(
                                "granteeUserId", uncleC.userId().toString(),
                                "permission", "NODE_CLAIM_APPROVE",
                                "scopeType", "SUBTREE",
                                "scopeRefId", UUID.randomUUID().toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PERSON_NOT_FOUND"));
    }

    private org.springframework.test.web.servlet.ResultActions rename(
            Actor actor, UUID personId, String newName) throws Exception {

        JsonNode current =
                body(
                        as(actor, get("/api/v1/clans/" + clanId + "/persons/" + personId))
                                .andReturn());
        return as(
                actor,
                patch("/api/v1/clans/" + clanId + "/persons/" + personId),
                Map.of("fullName", newName, "version", current.get("version").asLong()));
    }
}
