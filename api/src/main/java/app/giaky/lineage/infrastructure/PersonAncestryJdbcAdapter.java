package app.giaky.lineage.infrastructure;

import app.giaky.common.types.ClanId;
import app.giaky.common.types.PersonId;
import app.giaky.lineage.domain.AncestryRepository;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * {@code person_ancestry} over plain JDBC (doc 09 §2.3: native SQL for closure and tree reads).
 *
 * <p>Not JPA, and not because JPA could not map the table. The three maintenance operations are set
 * operations — a cross product, a bounded recompute, a rebuild — and each is one statement the
 * database performs atomically. Expressed through an entity graph they become read-modify-write
 * loops over rows nobody wants in memory, with a window in the middle of each where the closure is
 * neither its old self nor its new one. Risk R3 is exactly that window.
 *
 * <p><b>It shares the caller's transaction and the caller's connection.</b> Spring's {@code
 * DataSourceUtils} hands {@link JdbcTemplate} the connection already bound to the active
 * transaction, so these statements and the JPA writes around them commit together (doc 02 §6). The
 * repositories above use {@code saveAndFlush}, so the row an edge statement reads has already
 * reached the database — an unflushed insert would make the cross product silently produce nothing.
 */
@Repository
public class PersonAncestryJdbcAdapter implements AncestryRepository {

    private final JdbcTemplate jdbc;

    public PersonAncestryJdbcAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean covers(ClanId clanId, PersonId ancestor, PersonId candidate) {
        Boolean found =
                jdbc.queryForObject(
                        """
                        SELECT EXISTS (SELECT 1
                                       FROM person_ancestry
                                       WHERE clan_id = ?
                                         AND ancestor_id = ?
                                         AND descendant_id = ?)
                        """,
                        Boolean.class,
                        clanId.value(),
                        ancestor.value(),
                        candidate.value());
        return Boolean.TRUE.equals(found);
    }

    @Override
    public Set<PersonId> descendantsOf(ClanId clanId, PersonId ancestor) {
        return toIds(
                jdbc.queryForList(
                        """
                        SELECT descendant_id
                        FROM person_ancestry
                        WHERE clan_id = ?
                          AND ancestor_id = ?
                        """,
                        UUID.class,
                        clanId.value(),
                        ancestor.value()));
    }

    @Override
    public Set<PersonId> descendantsOf(ClanId clanId, PersonId ancestor, int maxDepth) {
        return toIds(
                jdbc.queryForList(
                        """
                        SELECT descendant_id
                        FROM person_ancestry
                        WHERE clan_id = ?
                          AND ancestor_id = ?
                          AND depth <= ?
                        """,
                        UUID.class,
                        clanId.value(),
                        ancestor.value(),
                        maxDepth));
    }

    @Override
    public List<AncestorLink> ancestorsOf(ClanId clanId, PersonId descendant) {
        return jdbc.query(
                """
                SELECT ancestor_id, depth
                FROM person_ancestry
                WHERE clan_id = ?
                  AND descendant_id = ?
                ORDER BY depth
                """,
                (row, index) ->
                        new AncestorLink(
                                PersonId.of(row.getObject("ancestor_id", UUID.class)),
                                row.getInt("depth")),
                clanId.value(),
                descendant.value());
    }

    /**
     * The depth-0 self-row.
     *
     * <p>{@code ON CONFLICT DO NOTHING} makes it idempotent, which matters for the repair paths: a
     * rebuild followed by a create must not fail because the row it is about to write is already
     * correct.
     */
    @Override
    public void insertSelfRow(ClanId clanId, PersonId personId) {
        jdbc.update(
                """
                INSERT INTO person_ancestry (clan_id, ancestor_id, descendant_id, depth)
                VALUES (?, ?, ?, 0)
                ON CONFLICT (ancestor_id, descendant_id) DO NOTHING
                """,
                clanId.value(),
                personId.value(),
                personId.value());
    }

    /**
     * The cross product of doc 03 §3.10: {@code ancestors(parent) ∪ {parent}} × {@code
     * descendants(child) ∪ {child}}.
     *
     * <p>Both self-rows are already in the table, so the two halves of each union fall out of the
     * same two scans rather than needing a {@code UNION ALL}. Depths add through the new edge, and
     * {@code LEAST} on conflict keeps the shorter of two paths — the diamond case, where a couple's
     * child is reachable from a shared ancestor by two routes of different length.
     *
     * <p>Requires that the edge does not close a cycle; {@code LineageGraphService.requireNoCycle}
     * is what guarantees it, and without that guarantee this statement has no finite result.
     */
    @Override
    public void addEdge(ClanId clanId, PersonId parentId, PersonId childId) {
        jdbc.update(
                """
                INSERT INTO person_ancestry (clan_id, ancestor_id, descendant_id, depth)
                SELECT ?, above.ancestor_id, below.descendant_id, above.depth + 1 + below.depth
                FROM person_ancestry above
                         CROSS JOIN person_ancestry below
                WHERE above.clan_id = ?
                  AND above.descendant_id = ?
                  AND below.clan_id = ?
                  AND below.ancestor_id = ?
                ON CONFLICT (ancestor_id, descendant_id)
                    DO UPDATE SET depth = LEAST(person_ancestry.depth, EXCLUDED.depth)
                """,
                clanId.value(),
                clanId.value(),
                parentId.value(),
                clanId.value(),
                childId.value());
    }

    @Override
    public void recomputeFrom(ClanId clanId, PersonId personId) {
        jdbc.queryForObject(
                "SELECT recompute_person_ancestry_from(?, ?)",
                Long.class,
                clanId.value(),
                personId.value());
    }

    @Override
    public long rebuild(ClanId clanId) {
        Long rows =
                jdbc.queryForObject(
                        "SELECT rebuild_person_ancestry(?)", Long.class, clanId.value());
        return rows == null ? 0L : rows;
    }

    private static Set<PersonId> toIds(List<UUID> raw) {
        Set<PersonId> ids = new LinkedHashSet<>(raw.size());
        for (UUID value : raw) {
            ids.add(PersonId.of(value));
        }
        return ids;
    }
}
