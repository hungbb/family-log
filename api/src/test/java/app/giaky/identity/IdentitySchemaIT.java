package app.giaky.identity;

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
 * What V2, V3 and V3.1 actually guarantee.
 *
 * <p>The application enforces these rules too, and that is the point of asserting them here: a
 * constraint the database also holds survives a bug in the layer above it, and an invariant that
 * lives only in Java is one migration away from being untrue in the data.
 */
class IdentitySchemaIT extends AbstractIntegrationTest {

    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("the Phase 1 migrations all applied")
    void migrationsApplied() {
        List<String> versions =
                jdbcTemplate.queryForList(
                        "SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank",
                        String.class);

        assertThat(versions).contains("1", "2", "3", "3.1");
    }

    @Test
    @DisplayName("I-U1: two spellings of one address cannot both be stored")
    void emailIsUniqueCaseInsensitively() {
        UUID first = insertUser("an@example.com");

        // The row itself is refused by ck_users_email_normalized before the index is even reached,
        // which is the belt-and-braces the migration comment describes.
        assertThatThrownBy(() -> insertUser("AN@EXAMPLE.COM")).isInstanceOf(DataAccessException.class);
        assertThat(first).isNotNull();
    }

    @Test
    @DisplayName("an un-normalized address is refused by the check constraint")
    void unnormalizedEmailIsRefused() {
        assertThatThrownBy(() -> insertUser("  An@Example.com  "))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    @DisplayName("an unknown status is refused")
    void statusIsConstrained() {
        assertThatThrownBy(
                        () ->
                                jdbcTemplate.update(
                                        """
                                        INSERT INTO users (id, email, password_hash, display_name, status)
                                        VALUES (?, ?, '$2a$12$x', 'An', 'BANISHED')
                                        """,
                                        UUID.randomUUID(),
                                        "an-" + UUID.randomUUID() + "@example.com"))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    @DisplayName("an unsupported locale is refused")
    void localeIsConstrained() {
        assertThatThrownBy(
                        () ->
                                jdbcTemplate.update(
                                        """
                                        INSERT INTO users (id, email, password_hash, display_name, status, locale)
                                        VALUES (?, ?, '$2a$12$x', 'An', 'ACTIVE', 'fr-FR')
                                        """,
                                        UUID.randomUUID(),
                                        "an-" + UUID.randomUUID() + "@example.com"))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    @DisplayName("a refresh token cannot be half-revoked")
    void revocationColumnsAreSetTogether() {
        UUID user = insertUser("an-" + UUID.randomUUID() + "@example.com");

        assertThatThrownBy(
                        () ->
                                jdbcTemplate.update(
                                        """
                                        INSERT INTO refresh_tokens
                                            (id, user_id, chain_id, token_hash, issued_at, expires_at, revoked_at)
                                        VALUES (?, ?, ?, ?, now(), now() + interval '30 days', now())
                                        """,
                                        UUID.randomUUID(),
                                        user,
                                        UUID.randomUUID(),
                                        UUID.randomUUID().toString()))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    @DisplayName("deleting an account takes its sessions and tokens with it")
    void tokensCascadeFromTheAccount() {
        UUID user = insertUser("an-" + UUID.randomUUID() + "@example.com");
        insertRefreshToken(user);

        jdbcTemplate.update("DELETE FROM users WHERE id = ?", user);

        Integer remaining =
                jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM refresh_tokens WHERE user_id = ?", Integer.class, user);
        assertThat(remaining).isZero();
    }

    @Test
    @DisplayName("audit_entries is append-only: an update or a delete is refused by the database")
    void auditEntriesAreAppendOnly() {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO audit_entries (id, action, target_type, occurred_at)
                VALUES (?, 'USER_LOGIN', 'USER', ?)
                """,
                id,
                java.sql.Timestamp.from(Instant.now()));

        assertThatThrownBy(
                        () ->
                                jdbcTemplate.update(
                                        "UPDATE audit_entries SET action = 'TAMPERED' WHERE id = ?", id))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(
                        () -> jdbcTemplate.update("DELETE FROM audit_entries WHERE id = ?", id))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    @DisplayName("an avatar reference must point at a real media object")
    void avatarForeignKeyIsEnforced() {
        UUID user = insertUser("an-" + UUID.randomUUID() + "@example.com");

        assertThatThrownBy(
                        () ->
                                jdbcTemplate.update(
                                        "UPDATE users SET avatar_media_id = ? WHERE id = ?",
                                        UUID.randomUUID(),
                                        user))
                .isInstanceOf(DataAccessException.class);
    }

    private UUID insertUser(String email) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO users (id, email, password_hash, display_name, status)
                VALUES (?, ?, '$2a$12$x', 'An', 'ACTIVE')
                """,
                id,
                email);
        return id;
    }

    private void insertRefreshToken(UUID user) {
        jdbcTemplate.update(
                """
                INSERT INTO refresh_tokens
                    (id, user_id, chain_id, token_hash, issued_at, expires_at)
                VALUES (?, ?, ?, ?, now(), now() + interval '30 days')
                """,
                UUID.randomUUID(),
                user,
                UUID.randomUUID(),
                UUID.randomUUID().toString());
    }
}
