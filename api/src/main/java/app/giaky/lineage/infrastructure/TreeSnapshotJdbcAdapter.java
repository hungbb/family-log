package app.giaky.lineage.infrastructure;

import app.giaky.common.types.ClanId;
import app.giaky.common.types.DatePrecision;
import app.giaky.common.types.PartialDate;
import app.giaky.common.types.PersonId;
import app.giaky.lineage.domain.Gender;
import app.giaky.lineage.domain.LifeStatus;
import app.giaky.lineage.domain.ParentRole;
import app.giaky.lineage.domain.ParentageNature;
import app.giaky.lineage.domain.TreeSnapshotRepository;
import app.giaky.lineage.domain.UnionStatus;
import app.giaky.lineage.domain.UnionType;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * The three flat scans behind {@code GET /tree} (B5.2).
 *
 * <p>Plain JDBC rather than JPA, for the reason {@link TreeSnapshotRepository} states: the tree read
 * wants fourteen columns per person and no persistence context at all, and an entity-based read
 * gives it the whole aggregate plus a dirty-checking snapshot of every row. Doc 09 §2.3 names native
 * SQL as the tool for closure and tree reads, and this is the tree read.
 *
 * <p><b>Every statement leads with {@code clan_id}</b>, which is the tenancy predicate and the index
 * these tables are organized around. A person id from a URL never reaches these queries without one.
 *
 * <p><b>Edges join both of their endpoints.</b> Soft-deleting a person leaves their edges alone on
 * purpose — restoring is a timestamp, not a repair (I-P7) — so an edge can outlive the node at one
 * end of it. A payload carrying that edge is a payload the client draws a line into empty space
 * from, which is why both joins filter on {@code deleted_at} rather than the person query alone.
 *
 * <p><b>Id sets are bound as one text parameter</b>, split by PostgreSQL into a {@code UUID[]}. The
 * alternative — an {@code IN} list of five thousand placeholders — is a different prepared statement
 * for every subtree size, which defeats the plan cache and eventually the parameter limit. The same
 * trick, for the same reason, is what {@code PersonJpaRepository.updateGenerations} uses.
 */
@Repository
public class TreeSnapshotJdbcAdapter implements TreeSnapshotRepository {

    private static final String PERSON_COLUMNS =
            """
            SELECT p.id, p.clan_id, p.branch_id, p.full_name, p.alias,
                   p.gender, p.life_status,
                   p.birth_year, p.birth_month, p.birth_day, p.birth_precision,
                   p.birth_circa, p.birth_range_end_year, p.birth_lunar_text,
                   p.death_year, p.death_month, p.death_day, p.death_precision,
                   p.death_circa, p.death_range_end_year, p.death_lunar_text,
                   p.generation, p.birth_order, p.avatar_media_id, p.linked_user_id,
                   p.is_founding_ancestor
            FROM persons p
            """;

    private static final String EDGE_COLUMNS =
            """
            SELECT e.id, e.parent_person_id, e.child_person_id, e.parent_role, e.nature, e.union_id
            FROM parentages e
                     JOIN persons pp ON pp.id = e.parent_person_id AND pp.deleted_at IS NULL
                     JOIN persons cp ON cp.id = e.child_person_id AND cp.deleted_at IS NULL
            """;

    private static final String UNION_COLUMNS =
            """
            SELECT u.id, u.partner_a_id, u.partner_b_id, u.union_type, u.status,
                   u.start_year, u.start_month, u.start_day, u.start_precision,
                   u.start_circa, u.start_range_end_year, u.start_lunar_text,
                   u.end_year, u.end_month, u.end_day, u.end_precision,
                   u.end_circa, u.end_range_end_year, u.end_lunar_text,
                   u.notes
            FROM unions u
                     JOIN persons pa ON pa.id = u.partner_a_id AND pa.deleted_at IS NULL
                     JOIN persons pb ON pb.id = u.partner_b_id AND pb.deleted_at IS NULL
            """;

    /** The right-hand side of {@code = ANY(...)}: a comma-separated id list, cast by PostgreSQL. */
    private static final String ID_ARRAY = "CAST(string_to_array(CAST(? AS TEXT), ',') AS UUID[])";

    private final JdbcTemplate jdbc;

    public TreeSnapshotJdbcAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public TreeSnapshot ofClan(ClanId clanId) {
        UUID clan = clanId.value();

        List<NodeRow> persons =
                jdbc.query(
                        PERSON_COLUMNS + "WHERE p.clan_id = ? AND p.deleted_at IS NULL",
                        NODE,
                        clan);

        List<EdgeRow> parentages =
                jdbc.query(
                        EDGE_COLUMNS + "WHERE e.clan_id = ? AND e.deleted_at IS NULL", EDGE, clan);

        List<UnionRow> unions =
                jdbc.query(
                        UNION_COLUMNS + "WHERE u.clan_id = ? AND u.deleted_at IS NULL", UNION, clan);

        return new TreeSnapshot(persons, parentages, unions);
    }

    @Override
    public TreeSnapshot ofPersons(ClanId clanId, Collection<PersonId> personIds) {
        if (personIds.isEmpty()) {
            return TreeSnapshot.empty();
        }
        UUID clan = clanId.value();
        String ids =
                personIds.stream()
                        .map(personId -> personId.value().toString())
                        .collect(Collectors.joining(","));

        List<NodeRow> persons =
                jdbc.query(
                        PERSON_COLUMNS
                                + "WHERE p.clan_id = ? AND p.deleted_at IS NULL AND p.id = ANY ("
                                + ID_ARRAY
                                + ")",
                        NODE,
                        clan,
                        ids);

        // Both endpoints inside the set, decided in SQL. Filtering in Java would mean fetching every
        // edge of the clan and throwing most of them away, which is the cost a subtree read exists
        // to avoid.
        List<EdgeRow> parentages =
                jdbc.query(
                        EDGE_COLUMNS
                                + "WHERE e.clan_id = ? AND e.deleted_at IS NULL"
                                + " AND e.parent_person_id = ANY ("
                                + ID_ARRAY
                                + ") AND e.child_person_id = ANY ("
                                + ID_ARRAY
                                + ")",
                        EDGE,
                        clan,
                        ids,
                        ids);

        List<UnionRow> unions =
                jdbc.query(
                        UNION_COLUMNS
                                + "WHERE u.clan_id = ? AND u.deleted_at IS NULL"
                                + " AND u.partner_a_id = ANY ("
                                + ID_ARRAY
                                + ") AND u.partner_b_id = ANY ("
                                + ID_ARRAY
                                + ")",
                        UNION,
                        clan,
                        ids,
                        ids);

        return new TreeSnapshot(persons, parentages, unions);
    }

    // --- row mapping ---------------------------------------------------------------------------

    private static final RowMapper<NodeRow> NODE =
            (row, index) ->
                    new NodeRow(
                            row.getObject("id", UUID.class),
                            row.getObject("clan_id", UUID.class),
                            row.getObject("branch_id", UUID.class),
                            row.getString("full_name"),
                            row.getString("alias"),
                            Gender.valueOf(row.getString("gender")),
                            LifeStatus.valueOf(row.getString("life_status")),
                            date(row, "birth_"),
                            date(row, "death_"),
                            integer(row, "generation"),
                            integer(row, "birth_order"),
                            row.getObject("avatar_media_id", UUID.class),
                            row.getObject("linked_user_id", UUID.class),
                            row.getBoolean("is_founding_ancestor"));

    private static final RowMapper<EdgeRow> EDGE =
            (row, index) ->
                    new EdgeRow(
                            row.getObject("id", UUID.class),
                            row.getObject("parent_person_id", UUID.class),
                            row.getObject("child_person_id", UUID.class),
                            ParentRole.valueOf(row.getString("parent_role")),
                            ParentageNature.valueOf(row.getString("nature")),
                            row.getObject("union_id", UUID.class));

    private static final RowMapper<UnionRow> UNION =
            (row, index) ->
                    new UnionRow(
                            row.getObject("id", UUID.class),
                            row.getObject("partner_a_id", UUID.class),
                            row.getObject("partner_b_id", UUID.class),
                            UnionType.valueOf(row.getString("union_type")),
                            UnionStatus.valueOf(row.getString("status")),
                            date(row, "start_"),
                            date(row, "end_"),
                            row.getString("notes"));

    /**
     * One of the seven-column partial dates, by column prefix.
     *
     * <p>Goes through {@link PartialDate}'s constructor rather than around it, exactly as {@link
     * PartialDateEmbeddable} does on the JPA path: a row hand-edited into a day with no month should
     * fail on read rather than reach a comparison that quietly answers wrongly.
     */
    private static PartialDate date(ResultSet row, String prefix) throws SQLException {
        String precision = row.getString(prefix + "precision");
        return new PartialDate(
                integer(row, prefix + "year"),
                integer(row, prefix + "month"),
                integer(row, prefix + "day"),
                precision == null ? DatePrecision.UNKNOWN : DatePrecision.valueOf(precision),
                row.getBoolean(prefix + "circa"),
                integer(row, prefix + "range_end_year"),
                row.getString(prefix + "lunar_text"));
    }

    /** {@code SMALLINT} reads back as 0 for SQL NULL through {@code getInt}; this does not. */
    private static Integer integer(ResultSet row, String column) throws SQLException {
        int value = row.getInt(column);
        return row.wasNull() ? null : value;
    }
}
