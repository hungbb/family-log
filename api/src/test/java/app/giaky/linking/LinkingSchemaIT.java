package app.giaky.linking;

import static org.assertj.core.api.Assertions.assertThat;

import app.giaky.support.AbstractIntegrationTest;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * What V11, V11.1 and V13 actually created.
 *
 * <p>Constraints and partial indexes are the half of an invariant the application cannot enforce
 * under a race, so they are worth asserting on their own: a migration that quietly shipped {@code
 * ux_claim_pending} without its {@code WHERE} clause would still pass every service test and would
 * refuse a second claim after the first was rejected.
 */
class LinkingSchemaIT extends AbstractIntegrationTest {

    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("the claim tables exist")
    void tablesExist() {
        assertThat(tableNames())
                .contains("node_claim_requests", "person_profile_propagation", "notifications");
    }

    @Test
    @DisplayName("I-K3: one pending claim per requester and node, and only while pending")
    void pendingClaimIndexIsPartial() {
        assertThat(indexDefinition("ux_claim_pending"))
                .contains("UNIQUE")
                .contains("requester_user_id")
                .contains("person_id")
                .contains("WHERE (status");
    }

    @Test
    @DisplayName("a claim that has left PENDING carries when it left")
    void decisionIsPaired() {
        assertThat(constraintDefinition("ck_claim_decision_paired"))
                .contains("decided_at");
    }

    @Test
    @DisplayName("the claim status vocabulary is closed, and has no EXPIRED in it")
    void statusIsChecked() {
        String definition = constraintDefinition("ck_claim_status");

        assertThat(definition).contains("PENDING", "APPROVED", "REJECTED", "CANCELLED");
        // Deliberate: a claim nobody answers is a relative still waiting, not a stale request.
        assertThat(definition).doesNotContain("EXPIRED");
    }

    @Test
    @DisplayName("I-P4 is still a partial unique index on the persons table")
    void oneNodePerUserPerClan() {
        assertThat(indexDefinition("ux_persons_linked_user"))
                .contains("UNIQUE")
                .contains("clan_id")
                .contains("linked_user_id")
                .contains("WHERE");
    }

    @Test
    @DisplayName("propagation flags default to off in the table, not only in the code")
    void propagationDefaultsOff() {
        assertThat(columnDefault("person_profile_propagation", "propagate_avatar"))
                .isEqualTo("false");
        assertThat(columnDefault("person_profile_propagation", "propagate_display_name"))
                .isEqualTo("false");
    }

    @Test
    @DisplayName("the unread badge has the partial index it polls")
    void unreadIndexIsPartial() {
        assertThat(indexDefinition("ix_notif_unread"))
                .contains("recipient_user_id")
                .contains("WHERE (read_at IS NULL)");
    }

    private List<String> tableNames() {
        return jdbcTemplate.queryForList(
                "SELECT tablename FROM pg_tables WHERE schemaname = 'public'", String.class);
    }

    private String indexDefinition(String indexName) {
        return jdbcTemplate.queryForObject(
                "SELECT indexdef FROM pg_indexes WHERE indexname = ?", String.class, indexName);
    }

    private String constraintDefinition(String constraintName) {
        return jdbcTemplate.queryForObject(
                """
                SELECT pg_get_constraintdef(oid)
                FROM pg_constraint
                WHERE conname = ?
                """,
                String.class,
                constraintName);
    }

    private String columnDefault(String table, String column) {
        return jdbcTemplate.queryForObject(
                """
                SELECT column_default
                FROM information_schema.columns
                WHERE table_name = ? AND column_name = ?
                """,
                String.class,
                table,
                column);
    }
}
