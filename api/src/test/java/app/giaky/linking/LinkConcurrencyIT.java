package app.giaky.linking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.giaky.lineage.support.LineageApiSupport;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * <b>B6.11 — two approvals racing on one node resolve to a single winner.</b>
 *
 * <p>This is the one test in the phase that cannot be written honestly anywhere else. Every
 * in-memory double runs single-threaded, so the read-before-write in {@code PersonUserLinker} looks
 * sufficient in a unit test and is not: two requests can both pass it. What actually decides the
 * race is {@code linked_user_id IS NULL} in the {@code UPDATE}'s {@code WHERE} clause, and only a
 * real database with real row locks can demonstrate that.
 *
 * <p>Runs outside a transaction, because two connections cannot see each other's uncommitted work —
 * a test that held the fixture in an unflushed transaction would have both threads race against an
 * empty table. It therefore cleans up after itself, as {@code AbstractIntegrationTest} requires.
 */
@Transactional(propagation = Propagation.NEVER)
class LinkConcurrencyIT extends LineageApiSupport {

    @Autowired private JdbcTemplate jdbcTemplate;

    private UUID clanId;

    @AfterEach
    void removeTheClan() {
        if (clanId == null) {
            return;
        }
        // node_claim_requests holds persons with ON DELETE RESTRICT, and a branch may point at its
        // root person the same way. Both are cleared before the clan cascade runs.
        jdbcTemplate.update("DELETE FROM node_claim_requests WHERE clan_id = ?", clanId);
        jdbcTemplate.update("DELETE FROM person_profile_propagation WHERE clan_id = ?", clanId);
        jdbcTemplate.update(
                "UPDATE branches SET branch_root_person_id = NULL WHERE clan_id = ?", clanId);
        jdbcTemplate.update("DELETE FROM clans WHERE id = ?", clanId);
    }

    @Test
    @DisplayName("two approvers deciding the same node at once: exactly one succeeds")
    void exactlyOneWinner() throws Exception {
        Actor founder = signUp("Nguyễn Văn Tổ");
        JsonNode clan = createClan(founder, "Nguyễn Văn");
        clanId = UUID.fromString(clan.get("id").asText());
        String clanCode = clan.get("clanCode").asText();
        UUID rootBranch = rootBranchId(clan);

        UUID ancestor = foundingAncestor(founder, clanId, rootBranch, "Nguyễn Văn Ất");
        UUID contested = childOf(founder, clanId, ancestor, "Nguyễn Văn An", "MALE");

        Actor first = signUp("Người thứ nhất");
        Actor second = signUp("Người thứ hai");
        joinClan(first, founder, clanId, clanCode, null);
        joinClan(second, founder, clanId, clanCode, null);

        String firstClaim = claim(first, contested);
        String secondClaim = claim(second, contested);

        // Both approvals are issued by the founder, who covers the whole clan. Two approvers would
        // test the same statement and add a scope question this test is not about.
        List<Integer> statuses = raceApprovals(founder, firstClaim, secondClaim);

        assertThat(statuses)
                .as("one approval takes the node; the other is told it was taken")
                .containsExactlyInAnyOrder(200, 409);

        Integer linked =
                jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM persons WHERE id = ? AND linked_user_id IS NOT NULL",
                        Integer.class,
                        contested);
        assertThat(linked).isEqualTo(1);

        Integer approved =
                jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM node_claim_requests WHERE person_id = ?"
                                + " AND status = 'APPROVED'",
                        Integer.class,
                        contested);
        assertThat(approved).as("and exactly one claim records that it happened").isEqualTo(1);
    }

    /** Fires both approvals from a barrier, so they are in flight at the same moment. */
    private List<Integer> raceApprovals(Actor approver, String firstClaim, String secondClaim)
            throws Exception {

        CyclicBarrier startLine = new CyclicBarrier(2);
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            Future<Integer> left = pool.submit(approvalTask(approver, firstClaim, startLine));
            Future<Integer> right = pool.submit(approvalTask(approver, secondClaim, startLine));
            return List.of(left.get(), right.get());
        }
    }

    private Callable<Integer> approvalTask(Actor approver, String claimId, CyclicBarrier startLine) {
        return () -> {
            startLine.await();
            return as(
                            approver,
                            post("/api/v1/clans/" + clanId + "/claims/" + claimId + "/approve"),
                            Map.of())
                    .andReturn()
                    .getResponse()
                    .getStatus();
        };
    }

    private String claim(Actor actor, UUID personId) throws Exception {
        return body(
                        as(
                                        actor,
                                        post("/api/v1/clans/" + clanId + "/claims"),
                                        Map.of("personId", personId.toString()))
                                .andExpect(status().isCreated())
                                .andReturn())
                .get("id")
                .asText();
    }
}
