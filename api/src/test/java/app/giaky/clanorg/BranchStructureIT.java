package app.giaky.clanorg;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.giaky.clanorg.domain.Branch;
import app.giaky.clanorg.support.ClanOrgApiSupport;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The branch tree against real SQL.
 *
 * <p>This is where the recursive statement in {@code BranchJpaRepository} is exercised. The unit
 * tests run against a second implementation of that rewrite written in Java (see {@code
 * InMemoryBranchRepository}); if the two ever disagree, this is the test that says so.
 */
class BranchStructureIT extends ClanOrgApiSupport {

    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("nesting is allowed exactly ten deep, and the eleventh is refused (I-C7)")
    void depthCapIsEnforcedEndToEnd() throws Exception {
        Actor an = signUp("Nguyễn Văn An");
        JsonNode clan = createClan(an, "Nguyễn Văn");
        UUID clanId = UUID.fromString(clan.get("id").asText());

        UUID parent = null;
        for (int depth = 1; depth <= Branch.MAX_DEPTH; depth++) {
            JsonNode created = createBranch(an, clanId, parent, "Đời " + depth);
            assertThat(created.get("depth").asInt()).isEqualTo(depth);
            parent = UUID.fromString(created.get("id").asText());
        }

        as(
                        an,
                        post("/api/v1/clans/" + clanId + "/branches"),
                        Map.of("name", "one too many", "parentBranchId", parent.toString()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("a move rewrites every descendant path, and the depths stay consistent (I-C4)")
    void moveRewritesTheWholeSubtree() throws Exception {
        Actor an = signUp("Nguyễn Văn An");
        JsonNode clan = createClan(an, "Nguyễn Văn");
        UUID clanId = UUID.fromString(clan.get("id").asText());

        UUID chiTruong = id(createBranch(an, clanId, null, "Chi Trưởng"));
        UUID chiHai = id(createBranch(an, clanId, null, "Chi Hai"));
        UUID anGiang = id(createBranch(an, clanId, chiHai, "Nhánh An Giang"));
        UUID longXuyen = id(createBranch(an, clanId, anGiang, "Nhánh Long Xuyên"));
        UUID chauDoc = id(createBranch(an, clanId, longXuyen, "Nhánh Châu Đốc"));

        as(
                        an,
                        post("/api/v1/clans/" + clanId + "/branches/" + anGiang + "/move"),
                        Map.of("newParentBranchId", chiTruong.toString()))
                .andExpect(status().isOk());

        flushAndClear();
        assertPathsConsistent(clanId);

        // The three-branch subtree came with it and now hangs from Chi Trưởng.
        assertThat(pathOf(anGiang)).startsWith(pathOf(chiTruong));
        assertThat(pathOf(longXuyen)).startsWith(pathOf(anGiang));
        assertThat(pathOf(chauDoc)).startsWith(pathOf(longXuyen));
        assertThat(depthOf(anGiang)).isEqualTo(2);
        assertThat(depthOf(longXuyen)).isEqualTo(3);
        assertThat(depthOf(chauDoc)).isEqualTo(4);
        // And nothing was left behind under the old parent.
        assertThat(pathOf(anGiang)).doesNotContain(segmentOf(chiHai));
    }

    @Test
    @DisplayName("moving a branch under its own descendant is 422 CYCLE_DETECTED (I-C3)")
    void cycleIsRefused() throws Exception {
        Actor an = signUp("Nguyễn Văn An");
        UUID clanId = UUID.fromString(createClan(an, "Nguyễn Văn").get("id").asText());
        UUID chiHai = id(createBranch(an, clanId, null, "Chi Hai"));
        UUID anGiang = id(createBranch(an, clanId, chiHai, "Nhánh An Giang"));

        as(
                        an,
                        post("/api/v1/clans/" + clanId + "/branches/" + chiHai + "/move"),
                        Map.of("newParentBranchId", anGiang.toString()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("CYCLE_DETECTED"));

        // Refusing changed nothing.
        flushAndClear();
        assertPathsConsistent(clanId);
    }

    @Test
    @DisplayName("archiving a branch with live children is refused (I-C6)")
    void archivingWithLiveChildrenIsRefused() throws Exception {
        Actor an = signUp("Nguyễn Văn An");
        UUID clanId = UUID.fromString(createClan(an, "Nguyễn Văn").get("id").asText());
        UUID chiHai = id(createBranch(an, clanId, null, "Chi Hai"));
        UUID anGiang = id(createBranch(an, clanId, chiHai, "Nhánh An Giang"));

        as(an, post("/api/v1/clans/" + clanId + "/branches/" + chiHai + "/archive"))
                .andExpect(status().isUnprocessableEntity());

        // Archive the child first, and the parent becomes archivable.
        as(an, post("/api/v1/clans/" + clanId + "/branches/" + anGiang + "/archive"))
                .andExpect(status().isOk());
        as(an, post("/api/v1/clans/" + clanId + "/branches/" + chiHai + "/archive"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.archived").value(true));
    }

    @Test
    @DisplayName("the tree comes back nested, with per-branch member counts")
    void treeShape() throws Exception {
        Actor an = signUp("Nguyễn Văn An");
        Actor be = signUp("Nguyễn Thị Bé");
        JsonNode clan = createClan(an, "Nguyễn Văn");
        UUID clanId = UUID.fromString(clan.get("id").asText());
        UUID chiHai = id(createBranch(an, clanId, null, "Chi Hai"));
        joinClan(be, an, clanId, clan.get("clanCode").asText(), chiHai);

        JsonNode tree = body(as(an, get("/api/v1/clans/" + clanId + "/branches")).andReturn());

        assertThat(tree).hasSize(1);
        JsonNode root = tree.get(0);
        assertThat(root.get("memberCount").asLong()).isEqualTo(1);
        assertThat(root.get("children")).hasSize(1);
        assertThat(root.get("children").get(0).get("memberCount").asLong()).isEqualTo(1);
        assertThat(root.get("children").get(0).get("branchCode").asText()).startsWith("CHIHAI-");
    }

    // --- helpers -------------------------------------------------------------------------------

    @Test
    @DisplayName("renaming a clan or a branch to nothing but spaces is 422, not a 500")
    void blankNamesAreRefused() throws Exception {
        Actor an = signUp("Nguyễn Văn An");
        JsonNode clan = createClan(an, "Nguyễn Văn");
        UUID clanId = UUID.fromString(clan.get("id").asText());
        JsonNode chiHai = createBranch(an, clanId, null, "Chi Hai");

        as(
                        an,
                        patch("/api/v1/clans/" + clanId),
                        Map.of("name", "   ", "version", clan.get("version").asLong()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("name"));
        as(
                        an,
                        patch("/api/v1/clans/" + clanId + "/branches/" + id(chiHai)),
                        Map.of("name", " \t ", "version", chiHai.get("version").asLong()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("name"));

        // Leaving the name out is still how a PATCH says "leave it alone".
        as(
                        an,
                        patch("/api/v1/clans/" + clanId + "/branches/" + id(chiHai)),
                        Map.of("description", "Con cháu cụ Nhị", "version", chiHai.get("version").asLong()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Chi Hai"));
    }

    private static UUID id(JsonNode branch) {
        return UUID.fromString(branch.get("id").asText());
    }

    private String pathOf(UUID branchId) {
        return jdbcTemplate.queryForObject(
                "SELECT path FROM branches WHERE id = ?", String.class, branchId);
    }

    private int depthOf(UUID branchId) {
        Integer depth =
                jdbcTemplate.queryForObject(
                        "SELECT depth FROM branches WHERE id = ?", Integer.class, branchId);
        return depth == null ? -1 : depth;
    }

    private static String segmentOf(UUID branchId) {
        return branchId.toString().substring(0, 8);
    }

    /**
     * Re-derives every path from {@code parent_branch_id} and compares.
     *
     * <p>Deliberately does not compare paths to other paths: that would agree happily with a rewrite
     * that was consistently wrong. This is the same property {@code BranchPathPropertyTest} asserts
     * in memory, checked here against what PostgreSQL actually stored.
     */
    private void assertPathsConsistent(UUID clanId) {
        List<Map<String, Object>> rows =
                jdbcTemplate.queryForList(
                        """
                        SELECT id, parent_branch_id, path, depth
                        FROM branches
                        WHERE clan_id = ? AND deleted_at IS NULL
                        """,
                        clanId);

        Map<UUID, Map<String, Object>> byId = new java.util.HashMap<>();
        rows.forEach(row -> byId.put((UUID) row.get("id"), row));

        for (Map<String, Object> row : rows) {
            List<String> segments = new ArrayList<>();
            Map<String, Object> current = row;
            int guard = 0;
            while (current.get("parent_branch_id") != null) {
                segments.add(0, segmentOf((UUID) current.get("id")));
                current = byId.get((UUID) current.get("parent_branch_id"));
                assertThat(current).as("every branch reaches the root").isNotNull();
                assertThat(guard++).as("no cycle in the branch graph").isLessThan(Branch.MAX_DEPTH + 2);
            }
            segments.add(0, segmentOf((UUID) current.get("id")));

            String expected = "/" + String.join("/", segments) + "/";
            assertThat(row.get("path")).as("path of %s", row.get("id")).isEqualTo(expected);
            assertThat(((Number) row.get("depth")).intValue())
                    .as("depth of %s", row.get("id"))
                    .isEqualTo(segments.size() - 1);
        }
    }
}
