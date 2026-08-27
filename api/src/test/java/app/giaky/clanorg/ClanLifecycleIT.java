package app.giaky.clanorg;

import static org.assertj.core.api.Assertions.assertThat;
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Milestone M2's walking skeleton, through real HTTP against real PostgreSQL: found a clan, share
 * its code, join it, and see it in the switcher.
 *
 * <p>The unit tests already prove each step against in-memory repositories. This proves they
 * compose — which is the only thing an integration test is uniquely able to say.
 */
class ClanLifecycleIT extends ClanOrgApiSupport {

    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("found a clan, add branches, join by code, approve, switch")
    void theWholeFlow() throws Exception {
        Actor an = signUp("Nguyễn Văn An");
        Actor be = signUp("Nguyễn Thị Bé");

        // Found the clan. One request produces the clan, its root branch and An's membership.
        JsonNode clan = createClan(an, "Nguyễn Văn");
        UUID clanId = UUID.fromString(clan.get("id").asText());
        String clanCode = clan.get("clanCode").asText();

        assertThat(clanCode).startsWith("NGUYEN-");
        assertThat(clan.get("rootBranchId").isNull()).isFalse();
        assertThat(clan.get("memberCount").asLong()).isEqualTo(1);
        UUID rootBranchId = UUID.fromString(clan.get("rootBranchId").asText());

        // The three rows really exist, and the deferred foreign key really closed.
        flushAndClear();
        assertThat(countIn("clans", clanId)).isEqualTo(1);
        assertThat(
                        jdbcTemplate.queryForObject(
                                "SELECT root_branch_id FROM clans WHERE id = ?", UUID.class, clanId))
                .isEqualTo(rootBranchId);
        assertThat(
                        jdbcTemplate.queryForObject(
                                """
                                SELECT count(*) FROM clan_memberships
                                WHERE clan_id = ? AND user_id = ? AND status = 'ACTIVE'
                                """,
                                Integer.class,
                                clanId,
                                an.userId()))
                .isEqualTo(1);

        // Branches: Chi Trưởng, Chi Hai, and Chi Hai → Nhánh An Giang (the demo script).
        UUID chiTruong =
                UUID.fromString(createBranch(an, clanId, null, "Chi Trưởng").get("id").asText());
        UUID chiHai = UUID.fromString(createBranch(an, clanId, null, "Chi Hai").get("id").asText());
        UUID anGiang =
                UUID.fromString(
                        createBranch(an, clanId, chiHai, "Nhánh An Giang").get("id").asText());

        as(an, get("/api/v1/clans/" + clanId + "/branches"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(rootBranchId.toString()))
                .andExpect(jsonPath("$[0].children.length()").value(2));

        // Bé looks the clan up by code before asking to join.
        as(be, get("/api/v1/clans/lookup").param("code", clanCode))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Nguyễn Văn"))
                .andExpect(jsonPath("$.alreadyMember").value(false))
                // The card carries no clan id: the caller is not a member yet.
                .andExpect(jsonPath("$.clanCode").value(clanCode));

        joinClan(be, an, clanId, clanCode, chiHai);

        // Bé now sees the clan in her switcher, placed in Chi Hai.
        as(be, get("/api/v1/clans"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].clanCode").value(clanCode))
                .andExpect(jsonPath("$[0].homeBranchId").value(chiHai.toString()))
                .andExpect(jsonPath("$[0].myPermissions").value(org.hamcrest.Matchers.hasItem("CLAN_VIEW")));

        // And she is in the member list, with her name resolved from Identity.
        as(an, get("/api/v1/clans/" + clanId + "/members").param("branchId", chiHai.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].displayName").value("Nguyễn Thị Bé"))
                .andExpect(jsonPath("$.content[0].homeBranchName").value("Chi Hai"))
                .andExpect(jsonPath("$.content[0].isCreator").value(false));

        // An drags Nhánh An Giang under Chi Trưởng and the tree re-renders (demo step 5).
        as(
                        an,
                        post("/api/v1/clans/" + clanId + "/branches/" + anGiang + "/move"),
                        Map.of("newParentBranchId", chiTruong.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.parentBranchId").value(chiTruong.toString()))
                .andExpect(jsonPath("$.depth").value(2));
    }

    @Test
    @DisplayName("the founder can edit the clan; a plain member gets 403")
    void onlyTheFounderMayEdit() throws Exception {
        Actor an = signUp("Nguyễn Văn An");
        Actor be = signUp("Nguyễn Thị Bé");
        JsonNode clan = createClan(an, "Nguyễn Văn");
        UUID clanId = UUID.fromString(clan.get("id").asText());
        joinClan(be, an, clanId, clan.get("clanCode").asText(), null);

        as(
                        an,
                        patch("/api/v1/clans/" + clanId),
                        Map.of("description", "Gia phả họ Nguyễn", "version", clan.get("version").asLong()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("Gia phả họ Nguyễn"))
                // I-C1: the code is not editable and does not change.
                .andExpect(jsonPath("$.clanCode").value(clan.get("clanCode").asText()));

        as(be, patch("/api/v1/clans/" + clanId), Map.of("description", "khác", "version", 1))
                // A member already knows the clan exists, so they get a precise 403 (doc 04 §7).
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
    }

    @Test
    @DisplayName("a stale version is 409 VERSION_CONFLICT rather than a silent overwrite")
    void staleVersionConflicts() throws Exception {
        Actor an = signUp("Nguyễn Văn An");
        JsonNode clan = createClan(an, "Nguyễn Văn");
        UUID clanId = UUID.fromString(clan.get("id").asText());

        as(an, patch("/api/v1/clans/" + clanId), Map.of("name", "Đổi tên", "version", 99))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));
    }

    @Test
    @DisplayName("deleting a clan is soft, and it leaves everyone's switcher")
    void deleteIsSoft() throws Exception {
        Actor an = signUp("Nguyễn Văn An");
        JsonNode clan = createClan(an, "Nguyễn Văn");
        UUID clanId = UUID.fromString(clan.get("id").asText());

        as(an, delete("/api/v1/clans/" + clanId)).andExpect(status().isNoContent());

        as(an, get("/api/v1/clans"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        as(an, get("/api/v1/clans/" + clanId)).andExpect(status().isNotFound());

        flushAndClear();
        assertThat(countIn("clans", clanId)).isEqualTo(1);
    }

    @Test
    @DisplayName("a malformed code is 400, an unknown one 404")
    void lookupErrors() throws Exception {
        Actor an = signUp("Nguyễn Văn An");

        as(an, get("/api/v1/clans/lookup").param("code", "not a code"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_CLAN_CODE"));
        as(an, get("/api/v1/clans/lookup").param("code", "NGUYEN-ZZZZ"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CLAN_NOT_FOUND"));
    }

    @Test
    @DisplayName("the founder cannot leave: the clan would have nobody to administer it (I-M3)")
    void founderCannotLeave() throws Exception {
        Actor an = signUp("Nguyễn Văn An");
        UUID clanId = UUID.fromString(createClan(an, "Nguyễn Văn").get("id").asText());

        as(an, post("/api/v1/clans/" + clanId + "/members/leave"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("LAST_ADMIN_PROTECTED"));
    }

    @Test
    @DisplayName("a member can leave, and the clan then disappears for them")
    void memberCanLeave() throws Exception {
        Actor an = signUp("Nguyễn Văn An");
        Actor be = signUp("Nguyễn Thị Bé");
        JsonNode clan = createClan(an, "Nguyễn Văn");
        UUID clanId = UUID.fromString(clan.get("id").asText());
        joinClan(be, an, clanId, clan.get("clanCode").asText(), null);

        as(be, post("/api/v1/clans/" + clanId + "/members/leave")).andExpect(status().isNoContent());

        as(be, get("/api/v1/clans/" + clanId)).andExpect(status().isNotFound());
        as(an, get("/api/v1/clans/" + clanId + "/members"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    private int countIn(String table, UUID id) {
        Integer count =
                jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM " + table + " WHERE id = ?", Integer.class, id);
        return count == null ? 0 : count;
    }
}
