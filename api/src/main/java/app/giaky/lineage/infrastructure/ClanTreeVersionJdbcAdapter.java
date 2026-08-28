package app.giaky.lineage.infrastructure;

import app.giaky.common.types.ClanId;
import app.giaky.lineage.domain.TreeVersionRepository;
import java.sql.Timestamp;
import java.time.Clock;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * {@code clan_tree_versions} — the counter behind the tree-read {@code ETag} (B4.12).
 *
 * <p>Upsert-and-return, in one statement. The row is created on the clan's first lineage mutation
 * rather than alongside the clan itself, so Phase 2's creation path needs no change and a clan whose
 * tree has never been touched simply reads as version 0.
 *
 * <p>The increment is done by the database, not read-modify-written by the application: two editors
 * adding cousins at the same moment must produce two increments, and {@code tree_version + 1}
 * evaluated under the row lock is what guarantees that. A client would not notice a lost update —
 * the tag would still change — but a version that went <i>backwards</i> would serve one of them a
 * {@code 304} for a tree they have not seen.
 */
@Repository
public class ClanTreeVersionJdbcAdapter implements TreeVersionRepository {

    private final JdbcTemplate jdbc;
    private final Clock clock;

    public ClanTreeVersionJdbcAdapter(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Override
    public long current(ClanId clanId) {
        Long version =
                jdbc.queryForObject(
                        """
                        SELECT coalesce((SELECT tree_version
                                         FROM clan_tree_versions
                                         WHERE clan_id = ?), 0)
                        """,
                        Long.class,
                        clanId.value());
        return version == null ? 0L : version;
    }

    @Override
    public long bump(ClanId clanId) {
        Long version =
                jdbc.queryForObject(
                        """
                        INSERT INTO clan_tree_versions (clan_id, tree_version, updated_at)
                        VALUES (?, 1, ?)
                        ON CONFLICT (clan_id)
                            DO UPDATE SET tree_version = clan_tree_versions.tree_version + 1,
                                          updated_at = EXCLUDED.updated_at
                        RETURNING tree_version
                        """,
                        Long.class,
                        clanId.value(),
                        Timestamp.from(clock.instant()));
        return version == null ? 0L : version;
    }
}
