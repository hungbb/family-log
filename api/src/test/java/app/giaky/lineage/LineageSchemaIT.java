package app.giaky.lineage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.giaky.support.AbstractIntegrationTest;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * What V7 to V10 actually guarantee.
 *
 * <p>The application enforces these rules too, and that is exactly why they are asserted here: a
 * constraint the database also holds survives a bug in the layer above it, and an invariant that
 * lives only in Java is one migration away from being untrue in the data.
 *
 * <p>Everything is inserted with raw SQL, so a state no endpoint can produce can still be attempted.
 */
class LineageSchemaIT extends AbstractIntegrationTest {

    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("the Phase 4 migrations applied")
    void migrationsApplied() {
        List<String> versions =
                jdbcTemplate.queryForList(
                        "SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank",
                        String.class);

        assertThat(versions).contains("7", "8", "9", "10");
    }

    @Test
    @DisplayName("I-P1: a blank name is not a person")
    void nameMustNotBeBlank() {
        Fixture world = new Fixture();

        assertThatThrownBy(() -> world.insertPerson("   ", "UNKNOWN", null))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    @DisplayName("I-P3: somebody alive cannot carry a date of death")
    void aliveHasNoDeathDate() {
        Fixture world = new Fixture();

        assertThatThrownBy(
                        () ->
                                jdbcTemplate.update(
                                        """
                                        INSERT INTO persons
                                          (id, clan_id, branch_id, full_name, life_status, death_year,
                                           death_precision)
                                        VALUES (?, ?, ?, 'Nguyễn Văn An', 'ALIVE', 1968, 'YEAR')
                                        """,
                                        UUID.randomUUID(),
                                        world.clanId,
                                        world.branchId))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    @DisplayName("a day without a month is not a date anybody can render")
    void partialDateShapeIsChecked() {
        Fixture world = new Fixture();

        assertThatThrownBy(
                        () ->
                                jdbcTemplate.update(
                                        """
                                        INSERT INTO persons
                                          (id, clan_id, full_name, birth_year, birth_day, birth_precision)
                                        VALUES (?, ?, 'Nguyễn Văn An', 1901, 3, 'EXACT')
                                        """,
                                        UUID.randomUUID(),
                                        world.clanId))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    @DisplayName("I-P6: one founding ancestor per clan, and a deleted one frees the title")
    void oneFoundingAncestor() {
        Fixture world = new Fixture();
        UUID first = world.insertFoundingAncestor("Nguyễn Phúc Tổ");

        assertThatThrownBy(() -> world.insertFoundingAncestor("Kẻ mạo nhận"))
                .isInstanceOf(DataAccessException.class);

        jdbcTemplate.update("UPDATE persons SET deleted_at = now() WHERE id = ?", first);
        assertThat(world.insertFoundingAncestor("Người kế tục")).isNotNull();
    }

    @Test
    @DisplayName("I-P4: one node per user per clan")
    void oneNodePerUser() {
        Fixture world = new Fixture();
        UUID userId = world.insertUser();
        world.linkUser(world.insertPerson("Nguyễn Văn An", "UNKNOWN", null), userId);

        UUID second = world.insertPerson("Nguyễn Văn Hai", "UNKNOWN", null);
        assertThatThrownBy(() -> world.linkUser(second, userId))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    @DisplayName("I-R1 and I-R3: nobody is their own parent, and nobody has two fathers")
    void parentageConstraints() {
        Fixture world = new Fixture();
        UUID child = world.insertPerson("Nguyễn Văn Út", "UNKNOWN", null);
        UUID father = world.insertPerson("Nguyễn Văn Cha", "UNKNOWN", null);
        UUID pretender = world.insertPerson("Nguyễn Văn Khác", "UNKNOWN", null);

        assertThatThrownBy(() -> world.insertParentage(child, child, "FATHER"))
                .isInstanceOf(DataAccessException.class);

        world.insertParentage(father, child, "FATHER");
        assertThatThrownBy(() -> world.insertParentage(pretender, child, "FATHER"))
                .isInstanceOf(DataAccessException.class);

        // A mother is a different role and is allowed alongside.
        assertThat(world.insertParentage(pretender, child, "MOTHER")).isNotNull();
    }

    @Test
    @DisplayName("I-N1, I-N2: two different people, and only one live union per pair")
    void unionConstraints() {
        Fixture world = new Fixture();
        UUID one = world.insertPerson("Nguyễn Văn Hai", "UNKNOWN", null);
        UUID other = world.insertPerson("Trần Thị Hoa", "UNKNOWN", null);
        UUID smaller = one.compareTo(other) < 0 ? one : other;
        UUID larger = one.compareTo(other) < 0 ? other : one;

        assertThatThrownBy(() -> world.insertUnion(one, one))
                .isInstanceOf(DataAccessException.class);
        // The canonical ordering the unique index depends on is itself a CHECK.
        assertThatThrownBy(() -> world.insertUnion(larger, smaller))
                .isInstanceOf(DataAccessException.class);

        world.insertUnion(smaller, larger);
        assertThatThrownBy(() -> world.insertUnion(smaller, larger))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    @DisplayName("the closure's self-row is the only depth-0 row there may be")
    void ancestrySelfRow() {
        Fixture world = new Fixture();
        UUID one = world.insertPerson("Nguyễn Văn Hai", "UNKNOWN", null);
        UUID other = world.insertPerson("Nguyễn Văn Ba", "UNKNOWN", null);

        assertThatThrownBy(
                        () ->
                                jdbcTemplate.update(
                                        """
                                        INSERT INTO person_ancestry
                                          (clan_id, ancestor_id, descendant_id, depth)
                                        VALUES (?, ?, ?, 0)
                                        """,
                                        world.clanId,
                                        one,
                                        other))
                .as("a depth-0 row between two people is a cycle that got through")
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    @DisplayName("the name search vector is generated, unaccented, and indexed")
    void nameSearchIsGenerated() {
        Fixture world = new Fixture();
        world.insertPerson("Nguyễn Văn An", "UNKNOWN", null);

        Integer hits =
                jdbcTemplate.queryForObject(
                        """
                        SELECT count(*) FROM persons
                        WHERE clan_id = ?
                          AND name_search @@ plainto_tsquery('simple', f_unaccent('nguyen van an'))
                        """,
                        Integer.class,
                        world.clanId);

        assertThat(hits).as("doc 03 §6: diacritics must not be a barrier to finding a relative")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("rebuild_person_ancestry recomputes the closure from the edges")
    void rebuildFunction() {
        Fixture world = new Fixture();
        UUID grandparent = world.insertPerson("Nguyễn Phúc Tổ", "UNKNOWN", null);
        UUID parent = world.insertPerson("Nguyễn Văn An", "UNKNOWN", null);
        UUID child = world.insertPerson("Nguyễn Văn Bình", "UNKNOWN", null);
        world.insertParentage(grandparent, parent, "FATHER");
        world.insertParentage(parent, child, "FATHER");

        Long rows =
                jdbcTemplate.queryForObject(
                        "SELECT rebuild_person_ancestry(?)", Long.class, world.clanId);

        assertThat(rows).as("three self-rows, two edges, one grandparent link").isEqualTo(6L);
        assertThat(depthBetween(grandparent, child)).isEqualTo(2);
        assertThat(depthBetween(grandparent, grandparent)).isZero();
    }

    private Integer depthBetween(UUID ancestor, UUID descendant) {
        return jdbcTemplate.queryForObject(
                """
                SELECT depth FROM person_ancestry
                WHERE ancestor_id = ? AND descendant_id = ?
                """,
                Integer.class,
                ancestor,
                descendant);
    }

    /** A clan and a root branch to hang people from, inserted directly. */
    private final class Fixture {

        private final UUID clanId = UUID.randomUUID();
        private final UUID branchId = UUID.randomUUID();

        Fixture() {
            String suffix = clanId.toString().substring(0, 4).toUpperCase().replaceAll("[^A-HJKMNP-TV-Z0-9]", "9");
            jdbcTemplate.update(
                    """
                    INSERT INTO clans (id, clan_code, name, visibility)
                    VALUES (?, ?, 'Nguyễn Văn', 'PRIVATE')
                    """,
                    clanId,
                    "NGUYEN-" + suffix);
            jdbcTemplate.update(
                    """
                    INSERT INTO branches (id, clan_id, parent_branch_id, branch_code, name, path, depth)
                    VALUES (?, ?, NULL, ?, 'Họ', ?, 0)
                    """,
                    branchId,
                    clanId,
                    "HO-" + suffix,
                    "/" + branchId.toString().substring(0, 8) + "/");
        }

        UUID insertPerson(String name, String lifeStatus, UUID linkedUserId) {
            UUID id = UUID.randomUUID();
            jdbcTemplate.update(
                    """
                    INSERT INTO persons (id, clan_id, branch_id, full_name, life_status, linked_user_id)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """,
                    id,
                    clanId,
                    branchId,
                    name,
                    lifeStatus,
                    linkedUserId);
            return id;
        }

        UUID insertFoundingAncestor(String name) {
            UUID id = UUID.randomUUID();
            jdbcTemplate.update(
                    """
                    INSERT INTO persons (id, clan_id, branch_id, full_name, is_founding_ancestor, generation)
                    VALUES (?, ?, ?, ?, TRUE, 1)
                    """,
                    id,
                    clanId,
                    branchId,
                    name);
            return id;
        }

        UUID insertUser() {
            UUID id = UUID.randomUUID();
            jdbcTemplate.update(
                    """
                    INSERT INTO users (id, email, password_hash, display_name, status)
                    VALUES (?, ?, 'x', 'Người dùng', 'ACTIVE')
                    """,
                    id,
                    "lineage-" + id + "@example.com");
            return id;
        }

        void linkUser(UUID personId, UUID userId) {
            jdbcTemplate.update(
                    "UPDATE persons SET linked_user_id = ? WHERE id = ?", userId, personId);
        }

        UUID insertParentage(UUID parent, UUID child, String role) {
            UUID id = UUID.randomUUID();
            jdbcTemplate.update(
                    """
                    INSERT INTO parentages
                      (id, clan_id, parent_person_id, child_person_id, parent_role, nature)
                    VALUES (?, ?, ?, ?, ?, 'BIOLOGICAL')
                    """,
                    id,
                    clanId,
                    parent,
                    child,
                    role);
            return id;
        }

        UUID insertUnion(UUID partnerA, UUID partnerB) {
            UUID id = UUID.randomUUID();
            jdbcTemplate.update(
                    """
                    INSERT INTO unions (id, clan_id, partner_a_id, partner_b_id, union_type, status)
                    VALUES (?, ?, ?, ?, 'PRIMARY', 'UNKNOWN')
                    """,
                    id,
                    clanId,
                    partnerA,
                    partnerB);
            return id;
        }
    }
}
