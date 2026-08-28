package app.giaky.clanorg;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.giaky.support.AbstractIntegrationTest;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * What V4 and V5 actually guarantee.
 *
 * <p>The application enforces these rules too, and that is exactly why they are asserted here: a
 * constraint the database also holds survives a bug in the layer above it, and an invariant that
 * lives only in Java is one migration away from being untrue in the data.
 */
class ClanOrgSchemaIT extends AbstractIntegrationTest {

    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("the Phase 2 migrations applied")
    void migrationsApplied() {
        List<String> versions =
                jdbcTemplate.queryForList(
                        "SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank",
                        String.class);

        assertThat(versions).contains("4", "5");
    }

    @Test
    @DisplayName("I-C2: a clan cannot have two root branches")
    void oneRootPerClan() {
        UUID clanId = insertClan("NGUYEN-AAAA");
        insertBranch(clanId, null, "HO-AAAA", "/aaaaaaaa/", 0);

        assertThatThrownBy(() -> insertBranch(clanId, null, "HO-BBBB", "/bbbbbbbb/", 0))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    @DisplayName("I-C7: a branch deeper than ten is refused by the database, not only by the domain")
    void depthIsCapped() {
        UUID clanId = insertClan("NGUYEN-BBBB");
        UUID root = insertBranch(clanId, null, "HO-CCCC", "/aaaaaaaa/", 0);

        assertThatThrownBy(
                        () -> insertBranch(clanId, root, "CHI-DDDD", "/aaaaaaaa/bbbbbbbb/", 11))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    @DisplayName("only the root branch may be parentless, and only it may sit at depth 0")
    void parentAndDepthAgree() {
        UUID clanId = insertClan("NGUYEN-CCCC");
        UUID root = insertBranch(clanId, null, "HO-EEEE", "/aaaaaaaa/", 0);

        // A child claiming depth 0.
        assertThatThrownBy(() -> insertBranch(clanId, root, "CHI-FFFF", "/aaaaaaaa/bbbbbbbb/", 0))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    @DisplayName("a clan code is unique across every clan, deleted ones included (I-C1)")
    void clanCodesAreNeverReissued() {
        UUID clanId = insertClan("NGUYEN-DDDD");
        jdbcTemplate.update("UPDATE clans SET deleted_at = now() WHERE id = ?", clanId);

        // The uniqueness index deliberately carries no soft-delete predicate: a code somebody
        // shared last year must never start pointing at a different family.
        assertThatThrownBy(() -> insertClan("NGUYEN-DDDD")).isInstanceOf(DataAccessException.class);
    }

    @Test
    @DisplayName("a code that is not SURNAME-XXXX is refused")
    void codeShapeIsChecked() {
        assertThatThrownBy(() -> insertClan("nguyen-7f3k")).isInstanceOf(DataAccessException.class);
        // I, L, O and U are not in the Crockford alphabet.
        assertThatThrownBy(() -> insertClan("NGUYEN-7I3K")).isInstanceOf(DataAccessException.class);
    }

    @Test
    @DisplayName("two branches in one clan cannot claim the same path")
    void pathsAreUniqueWithinAClan() {
        UUID clanId = insertClan("NGUYEN-EEEE");
        UUID root = insertBranch(clanId, null, "HO-GGGG", "/aaaaaaaa/", 0);
        insertBranch(clanId, root, "CHI-HHHH", "/aaaaaaaa/bbbbbbbb/", 1);

        // A path collision would silently make one branch's descendant query return the other's
        // subtree. `ux_branches_path` turns it into an insert failure instead.
        assertThatThrownBy(
                        () -> insertBranch(clanId, root, "CHI-JJJJ", "/aaaaaaaa/bbbbbbbb/", 1))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    @DisplayName("I-M1: one active membership per (clan, user), and history is kept")
    void oneActiveMembershipPerUser() {
        UUID clanId = insertClan("NGUYEN-FFFF");
        UUID userId = insertUser();
        insertMembership(clanId, userId, "ACTIVE", null);

        assertThatThrownBy(() -> insertMembership(clanId, userId, "ACTIVE", null))
                .isInstanceOf(DataAccessException.class);

        // The index is partial, so somebody who left and rejoined keeps both rows.
        insertMembership(clanId, userId, "LEFT", Instant.now());
        assertThat(countMemberships(clanId, userId)).isEqualTo(2);
    }

    @Test
    @DisplayName("an active membership cannot record when it ended, and an ended one must")
    void statusAndLeftAtAgree() {
        UUID clanId = insertClan("NGUYEN-GGGG");
        UUID userId = insertUser();

        assertThatThrownBy(() -> insertMembership(clanId, userId, "ACTIVE", Instant.now()))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> insertMembership(clanId, userId, "REMOVED", null))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    @DisplayName("I-J1: one pending request per (requester, clan, target branch)")
    void onePendingRequestPerTarget() {
        UUID clanId = insertClan("NGUYEN-HHHH");
        UUID userId = insertUser();
        insertJoinRequest(clanId, userId, null, "PENDING");

        assertThatThrownBy(() -> insertJoinRequest(clanId, userId, null, "PENDING"))
                .isInstanceOf(DataAccessException.class);

        // A decided request does not block a new one — the index is partial on PENDING.
        insertJoinRequest(clanId, userId, null, "REJECTED");
        assertThat(countJoinRequests(clanId, userId)).isEqualTo(2);
    }

    @Test
    @DisplayName("a pending request cannot carry a decision time, and a decided one must")
    void decisionTimeAndStatusAgree() {
        UUID clanId = insertClan("NGUYEN-JJJJ");
        UUID userId = insertUser();

        assertThatThrownBy(
                        () ->
                                jdbcTemplate.update(
                                        """
                                        INSERT INTO clan_join_requests
                                          (id, clan_id, requester_user_id, status, decided_at, expires_at)
                                        VALUES (?, ?, ?, 'PENDING', now(), now() + interval '90 days')
                                        """,
                                        UUID.randomUUID(),
                                        clanId,
                                        userId))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    @DisplayName("media objects can now point at a clan, as V3 promised they eventually would")
    void mediaClanForeignKeyExists() {
        Integer constraints =
                jdbcTemplate.queryForObject(
                        """
                        SELECT count(*) FROM pg_constraint
                        WHERE conname = 'fk_media_clan' AND contype = 'f'
                        """,
                        Integer.class);

        assertThat(constraints).isEqualTo(1);
    }

    @Test
    @DisplayName("branch_root_person_id got the foreign key V4 promised, once persons existed")
    void branchRootPersonForeignKeyArrived() {
        // Phase 2 created the column unconstrained because `persons` did not exist yet; V10 adds
        // the constraint alongside it (I-C5). This test asserted its absence for two phases and
        // now asserts its presence — the promise, kept.
        Integer column =
                jdbcTemplate.queryForObject(
                        """
                        SELECT count(*) FROM information_schema.columns
                        WHERE table_name = 'branches' AND column_name = 'branch_root_person_id'
                        """,
                        Integer.class);
        Integer constraints =
                jdbcTemplate.queryForObject(
                        """
                        SELECT count(*) FROM pg_constraint
                        WHERE conrelid = 'branches'::regclass AND contype = 'f'
                          AND conname LIKE '%root_person%'
                        """,
                        Integer.class);

        assertThat(column).isEqualTo(1);
        assertThat(constraints).isEqualTo(1);
    }

    // --- raw inserts, so a state no endpoint can produce can still be attempted ----------------

    private UUID insertClan(String code) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO clans (id, clan_code, name, visibility)
                VALUES (?, ?, 'Nguyễn Văn', 'PRIVATE')
                """,
                id,
                code);
        return id;
    }

    private UUID insertBranch(UUID clanId, UUID parentId, String code, String path, int depth) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO branches (id, clan_id, parent_branch_id, branch_code, name, path, depth)
                VALUES (?, ?, ?, ?, 'Chi', ?, ?)
                """,
                id,
                clanId,
                parentId,
                code,
                path,
                depth);
        return id;
    }

    private UUID insertUser() {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO users (id, email, password_hash, display_name, status)
                VALUES (?, ?, 'x', 'Người dùng', 'ACTIVE')
                """,
                id,
                "schema-" + id + "@example.com");
        return id;
    }

    private void insertMembership(UUID clanId, UUID userId, String status, Instant leftAt) {
        jdbcTemplate.update(
                """
                INSERT INTO clan_memberships (id, clan_id, user_id, status, left_at)
                VALUES (?, ?, ?, ?, ?)
                """,
                UUID.randomUUID(),
                clanId,
                userId,
                status,
                leftAt == null ? null : java.sql.Timestamp.from(leftAt));
    }

    private void insertJoinRequest(UUID clanId, UUID userId, UUID branchId, String status) {
        jdbcTemplate.update(
                """
                INSERT INTO clan_join_requests
                  (id, clan_id, target_branch_id, requester_user_id, status, decided_at, expires_at)
                VALUES (?, ?, ?, ?, ?, CASE WHEN ? = 'PENDING' THEN NULL ELSE now() END,
                        now() + interval '90 days')
                """,
                UUID.randomUUID(),
                clanId,
                branchId,
                userId,
                status,
                status);
    }

    private int countMemberships(UUID clanId, UUID userId) {
        Integer count =
                jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM clan_memberships WHERE clan_id = ? AND user_id = ?",
                        Integer.class,
                        clanId,
                        userId);
        return count == null ? 0 : count;
    }

    private int countJoinRequests(UUID clanId, UUID userId) {
        Integer count =
                jdbcTemplate.queryForObject(
                        """
                        SELECT count(*) FROM clan_join_requests
                        WHERE clan_id = ? AND requester_user_id = ?
                        """,
                        Integer.class,
                        clanId,
                        userId);
        return count == null ? 0 : count;
    }
}
