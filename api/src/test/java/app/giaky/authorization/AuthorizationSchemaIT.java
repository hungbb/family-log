package app.giaky.authorization;

import static org.assertj.core.api.Assertions.assertThat;

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
 * What V6 actually guarantees.
 *
 * <p>The application enforces all of this too — {@code PermissionScope} holds I-G1, {@code
 * PermissionGrant} holds I-G2, {@code GrantService} checks for duplicates before writing. That is
 * precisely why the constraints are asserted here as well: an invariant that lives only in Java is
 * one bad migration, one direct {@code psql} session or one forgotten code path away from being
 * untrue in the data, and this is the table where untrue data means somebody can edit a branch that
 * is not theirs.
 */
class AuthorizationSchemaIT extends AbstractIntegrationTest {

    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("the Phase 3 migration applied")
    void migrationApplied() {
        List<String> versions =
                jdbcTemplate.queryForList(
                        "SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank",
                        String.class);

        assertThat(versions).contains("6");
    }

    @Test
    @DisplayName("I-G1: a CLAN scope names nothing, and the others must name something")
    void scopeReferenceMatchesScopeType() {
        Fixture world = new Fixture();

        assertThatRefused(
                        () ->
                                insertGrant(
                                        world, "TREE_EDIT", "CLAN", UUID.randomUUID(), null, null))
                .isInstanceOf(DataAccessException.class);
        assertThatRefused(() -> insertGrant(world, "TREE_EDIT", "BRANCH", null, null, null))
                .isInstanceOf(DataAccessException.class);
        assertThatRefused(() -> insertGrant(world, "TREE_EDIT", "SUBTREE", null, null, null))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    @DisplayName("I-G2: CLAN_ADMIN cannot be scoped to a branch")
    void administratorIsClanWideOnly() {
        Fixture world = new Fixture();

        assertThatRefused(
                        () ->
                                insertGrant(
                                        world, "CLAN_ADMIN", "BRANCH", world.branchId, null, null))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    @DisplayName("MEMBER_APPROVE has no subtree form")
    void memberApproveHasNoSubtreeForm() {
        Fixture world = new Fixture();

        assertThatRefused(
                        () ->
                                insertGrant(
                                        world,
                                        "MEMBER_APPROVE",
                                        "SUBTREE",
                                        UUID.randomUUID(),
                                        null,
                                        null))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    @DisplayName("a permission outside the catalogue is refused")
    void permissionVocabularyIsClosed() {
        Fixture world = new Fixture();

        assertThatRefused(() -> insertGrant(world, "PATRIARCH", "CLAN", null, null, null))
                .isInstanceOf(DataAccessException.class);
        assertThatRefused(() -> insertGrant(world, "TREE_EDIT", "HOUSEHOLD", null, null, null))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    @DisplayName("I-G5: one live grant per (person, clan, permission, scope), and history is kept")
    void oneLiveGrantPerScope() {
        Fixture world = new Fixture();
        insertGrant(world, "TREE_EDIT", "BRANCH", world.branchId, null, null);

        assertThatRefused(
                        () -> insertGrant(world, "TREE_EDIT", "BRANCH", world.branchId, null, null))
                .isInstanceOf(DataAccessException.class);

        // The index is partial on revoked_at, so a permission granted, taken back and granted again
        // leaves three rows rather than overwriting the story.
        jdbcTemplate.update(
                "UPDATE permission_grants SET revoked_at = now(), revoked_by = ? WHERE clan_id = ?",
                world.userId,
                world.clanId);
        insertGrant(world, "TREE_EDIT", "BRANCH", world.branchId, null, null);

        assertThat(countGrants(world)).isEqualTo(2);
    }

    @Test
    @DisplayName("the clan-wide case collides with itself, which NULLs alone would not")
    void clanScopeCollidesWithItself() {
        Fixture world = new Fixture();
        insertGrant(world, "TREE_EDIT", "CLAN", null, null, null);

        // Two NULL scope_ref_ids never compare equal, so the index COALESCEs them to a fixed UUID.
        // Without that, "clan-wide TREE_EDIT" could be granted any number of times.
        assertThatRefused(() -> insertGrant(world, "TREE_EDIT", "CLAN", null, null, null))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    @DisplayName("a live grant cannot name who revoked it")
    void revocationColumnsAgree() {
        Fixture world = new Fixture();

        assertThatRefused(
                        () ->
                                insertGrant(
                                        world, "TREE_EDIT", "CLAN", null, null, world.userId))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    @DisplayName("the expiry sweep may revoke without an actor")
    void systemRevocationIsAllowed() {
        Fixture world = new Fixture();
        insertGrant(world, "TREE_EDIT", "CLAN", null, null, null);

        jdbcTemplate.update(
                "UPDATE permission_grants SET revoked_at = now() WHERE clan_id = ?", world.clanId);

        assertThat(countGrants(world)).isEqualTo(1);
    }

    @Test
    @DisplayName("an expiry before the grant was issued is refused")
    void expiryFollowsIssue() {
        Fixture world = new Fixture();

        assertThatRefused(
                        () ->
                                jdbcTemplate.update(
                                        """
                                        INSERT INTO permission_grants
                                          (id, clan_id, grantee_user_id, permission, scope_type,
                                           granted_by, granted_at, expires_at)
                                        VALUES (?, ?, ?, 'TREE_EDIT', 'CLAN', ?, now(),
                                                now() - interval '1 day')
                                        """,
                                        UUID.randomUUID(),
                                        world.clanId,
                                        world.userId,
                                        world.userId))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    @DisplayName("the hot-path index exists and is partial on revoked_at")
    void lookupIndexIsPartial() {
        String definition =
                jdbcTemplate.queryForObject(
                        "SELECT indexdef FROM pg_indexes WHERE indexname = 'ix_grant_lookup'",
                        String.class);

        // Partial so the index stays the size of what is currently in force rather than of
        // everything ever issued (doc 03 §3.11).
        assertThat(definition).contains("grantee_user_id").contains("clan_id");
        assertThat(definition).contains("WHERE (revoked_at IS NULL)");
    }

    @Test
    @DisplayName("audit entries can record the grant that authorised them (FR-Z8)")
    void auditCarriesTheAuthorizingGrant() {
        Integer column =
                jdbcTemplate.queryForObject(
                        """
                        SELECT count(*) FROM information_schema.columns
                        WHERE table_name = 'audit_entries' AND column_name = 'authorizing_grant_id'
                        """,
                        Integer.class);

        // Created by V3.1 in Phase 1, deliberately ahead of the grants it refers to, which is why
        // doc 03 §5's V12b is not a migration this phase needs.
        assertThat(column).isEqualTo(1);
    }

    // --- raw inserts, so a state no endpoint can produce can still be attempted ------------------

    /** A clan, a branch and an account to hang grants on. */
    private final class Fixture {

        private final UUID clanId = UUID.randomUUID();
        private final UUID branchId = UUID.randomUUID();
        private final UUID userId = UUID.randomUUID();

        private Fixture() {
            String suffix = Long.toHexString(System.nanoTime()).toUpperCase().substring(0, 4);
            jdbcTemplate.update(
                    """
                    INSERT INTO clans (id, clan_code, name, visibility)
                    VALUES (?, ?, 'Nguyễn Văn', 'PRIVATE')
                    """,
                    clanId,
                    "NGUYEN-" + suffix.replace('I', 'A').replace('L', 'B').replace('O', 'C')
                            .replace('U', 'D'));
            jdbcTemplate.update(
                    """
                    INSERT INTO branches (id, clan_id, parent_branch_id, branch_code, name, path, depth)
                    VALUES (?, ?, NULL, ?, 'Chi', ?, 0)
                    """,
                    branchId,
                    clanId,
                    "HO-" + suffix.replace('I', 'A').replace('L', 'B').replace('O', 'C')
                            .replace('U', 'D'),
                    "/" + branchId.toString().substring(0, 8) + "/");
            jdbcTemplate.update(
                    """
                    INSERT INTO users (id, email, password_hash, display_name, status)
                    VALUES (?, ?, 'x', 'Người dùng', 'ACTIVE')
                    """,
                    userId,
                    "grant-schema-" + userId + "@example.com");
        }
    }

    private void insertGrant(
            Fixture world,
            String permission,
            String scopeType,
            UUID scopeRefId,
            Instant expiresAt,
            UUID revokedBy) {

        jdbcTemplate.update(
                """
                INSERT INTO permission_grants
                  (id, clan_id, grantee_user_id, permission, scope_type, scope_ref_id,
                   granted_by, granted_at, expires_at, revoked_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, now(), ?, ?)
                """,
                UUID.randomUUID(),
                world.clanId,
                world.userId,
                permission,
                scopeType,
                scopeRefId,
                world.userId,
                expiresAt == null ? null : java.sql.Timestamp.from(expiresAt),
                revokedBy);
    }

    private int countGrants(Fixture world) {
        Integer count =
                jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM permission_grants WHERE clan_id = ?",
                        Integer.class,
                        world.clanId);
        return count == null ? 0 : count;
    }
}
