package app.giaky.clanorg;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

/** Joining a clan end to end (FR-J1…FR-J5). */
class JoinFlowIT extends ClanOrgApiSupport {

    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("a duplicate pending request is 409 DUPLICATE_PENDING_REQUEST (I-J1)")
    void duplicateRequestIsRefused() throws Exception {
        Actor an = signUp("Nguyễn Văn An");
        Actor be = signUp("Nguyễn Thị Bé");
        String clanCode = createClan(an, "Nguyễn Văn").get("clanCode").asText();

        as(be, post("/api/v1/join-requests"), Map.of("clanCode", clanCode))
                .andExpect(status().isCreated());
        as(be, post("/api/v1/join-requests"), Map.of("clanCode", clanCode))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_PENDING_REQUEST"));
    }

    @Test
    @DisplayName("approval creates exactly one active membership, and a second approval is 409")
    void approvalIsIdempotentlyRefused() throws Exception {
        Actor an = signUp("Nguyễn Văn An");
        Actor be = signUp("Nguyễn Thị Bé");
        JsonNode clan = createClan(an, "Nguyễn Văn");
        UUID clanId = UUID.fromString(clan.get("id").asText());

        JsonNode request =
                body(
                        as(
                                        be,
                                        post("/api/v1/join-requests"),
                                        Map.of("clanCode", clan.get("clanCode").asText()))
                                .andExpect(status().isCreated())
                                .andReturn());
        String requestId = request.get("id").asText();

        as(an, post("/api/v1/clans/" + clanId + "/join-requests/" + requestId + "/approve"), Map.of())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        // The case this exists for is a double-clicked button.
        as(an, post("/api/v1/clans/" + clanId + "/join-requests/" + requestId + "/approve"), Map.of())
                .andExpect(status().isConflict());

        flushAndClear();
        assertThat(
                        jdbcTemplate.queryForObject(
                                """
                                SELECT count(*) FROM clan_memberships
                                WHERE clan_id = ? AND user_id = ? AND status = 'ACTIVE'
                                """,
                                Integer.class,
                                clanId,
                                be.userId()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("a member who asks to join again is 409 MEMBERSHIP_EXISTS (I-J2)")
    void memberCannotReapply() throws Exception {
        Actor an = signUp("Nguyễn Văn An");
        String clanCode = createClan(an, "Nguyễn Văn").get("clanCode").asText();

        as(an, post("/api/v1/join-requests"), Map.of("clanCode", clanCode))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MEMBERSHIP_EXISTS"));
    }

    @Test
    @DisplayName("a branch code aims the request at that branch and places the member there")
    void joinByBranchCode() throws Exception {
        Actor an = signUp("Nguyễn Văn An");
        Actor be = signUp("Nguyễn Thị Bé");
        JsonNode clan = createClan(an, "Nguyễn Văn");
        UUID clanId = UUID.fromString(clan.get("id").asText());
        JsonNode chiHai = createBranch(an, clanId, null, "Chi Hai");

        JsonNode request =
                body(
                        as(
                                        be,
                                        post("/api/v1/join-requests"),
                                        Map.of("branchCode", chiHai.get("branchCode").asText()))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.targetBranchId").value(chiHai.get("id").asText()))
                                .andReturn());

        // Approving without naming a branch falls back to the one they asked for.
        as(
                        an,
                        post(
                                "/api/v1/clans/"
                                        + clanId
                                        + "/join-requests/"
                                        + request.get("id").asText()
                                        + "/approve"),
                        Map.of())
                .andExpect(status().isOk());

        as(be, get("/api/v1/clans"))
                .andExpect(jsonPath("$[0].homeBranchId").value(chiHai.get("id").asText()));
    }

    @Test
    @DisplayName("rejection keeps the reason and creates no membership")
    void rejection() throws Exception {
        Actor an = signUp("Nguyễn Văn An");
        Actor be = signUp("Nguyễn Thị Bé");
        JsonNode clan = createClan(an, "Nguyễn Văn");
        UUID clanId = UUID.fromString(clan.get("id").asText());

        JsonNode request =
                body(
                        as(
                                        be,
                                        post("/api/v1/join-requests"),
                                        Map.of("clanCode", clan.get("clanCode").asText()))
                                .andReturn());

        as(
                        an,
                        post(
                                "/api/v1/clans/"
                                        + clanId
                                        + "/join-requests/"
                                        + request.get("id").asText()
                                        + "/reject"),
                        Map.of("reason", "Không rõ là ai"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.decisionReason").value("Không rõ là ai"));

        as(be, get("/api/v1/clans")).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("a rejection with no reason is refused: a relative deserves an answer")
    void rejectionNeedsAReason() throws Exception {
        Actor an = signUp("Nguyễn Văn An");
        Actor be = signUp("Nguyễn Thị Bé");
        JsonNode clan = createClan(an, "Nguyễn Văn");
        UUID clanId = UUID.fromString(clan.get("id").asText());
        JsonNode request =
                body(
                        as(
                                        be,
                                        post("/api/v1/join-requests"),
                                        Map.of("clanCode", clan.get("clanCode").asText()))
                                .andReturn());

        as(
                        an,
                        post(
                                "/api/v1/clans/"
                                        + clanId
                                        + "/join-requests/"
                                        + request.get("id").asText()
                                        + "/reject"),
                        Map.of("reason", "  "))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("the requester sees their own requests, and can withdraw one")
    void myRequestsAndCancel() throws Exception {
        Actor an = signUp("Nguyễn Văn An");
        Actor be = signUp("Nguyễn Thị Bé");
        JsonNode clan = createClan(an, "Nguyễn Văn");

        JsonNode request =
                body(
                        as(
                                        be,
                                        post("/api/v1/join-requests"),
                                        Map.of("clanCode", clan.get("clanCode").asText()))
                                .andReturn());

        as(be, get("/api/v1/me/join-requests"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].clanName").value("Nguyễn Văn"))
                .andExpect(jsonPath("$[0].status").value("PENDING"));

        as(be, delete("/api/v1/join-requests/" + request.get("id").asText()))
                .andExpect(status().isNoContent());
        as(be, get("/api/v1/me/join-requests")).andExpect(jsonPath("$[0].status").value("CANCELLED"));
    }

    @Test
    @DisplayName("somebody else's request id is a 404, not an authorization question")
    void cannotCancelSomebodyElsesRequest() throws Exception {
        Actor an = signUp("Nguyễn Văn An");
        Actor be = signUp("Nguyễn Thị Bé");
        Actor stranger = signUp("Người lạ");
        JsonNode clan = createClan(an, "Nguyễn Văn");

        JsonNode request =
                body(
                        as(
                                        be,
                                        post("/api/v1/join-requests"),
                                        Map.of("clanCode", clan.get("clanCode").asText()))
                                .andReturn());

        as(stranger, delete("/api/v1/join-requests/" + request.get("id").asText()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("the inbox is the founder's alone, for now")
    void inboxIsRestricted() throws Exception {
        Actor an = signUp("Nguyễn Văn An");
        Actor be = signUp("Nguyễn Thị Bé");
        JsonNode clan = createClan(an, "Nguyễn Văn");
        UUID clanId = UUID.fromString(clan.get("id").asText());
        joinClan(be, an, clanId, clan.get("clanCode").asText(), null);

        as(an, get("/api/v1/clans/" + clanId + "/join-requests").param("status", "APPROVED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].requesterName").value("Nguyễn Thị Bé"));

        as(be, get("/api/v1/clans/" + clanId + "/join-requests"))
                .andExpect(status().isForbidden());
    }
}
