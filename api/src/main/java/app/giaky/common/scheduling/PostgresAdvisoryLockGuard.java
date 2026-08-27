package app.giaky.common.scheduling;

import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * {@link SingleRunGuard} on a PostgreSQL advisory lock.
 *
 * <p>{@code pg_try_advisory_lock} returns immediately: either this instance holds the lock and runs
 * the job, or another one does and this instance skips it. No queueing, no waiting — a scheduled job
 * that has already run this minute does not need to run again as soon as the lock frees.
 *
 * <p>The lock is session-scoped rather than transaction-scoped, so it is released in a {@code
 * finally}. A crash between the two releases it anyway, when the connection dies; that is the
 * property that makes an advisory lock safe to hold across work that might fail.
 *
 * <p>No new infrastructure for this. ShedLock or a coordination service would each be a dependency
 * to earn its keep on one job; the database is already there and already shared.
 */
@Component
public class PostgresAdvisoryLockGuard implements SingleRunGuard {

    private static final Logger log = LoggerFactory.getLogger(PostgresAdvisoryLockGuard.class);

    private final JdbcTemplate jdbcTemplate;

    public PostgresAdvisoryLockGuard(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public boolean runExclusively(String jobName, Runnable job) {
        long key = lockKey(jobName);
        Boolean acquired =
                jdbcTemplate.queryForObject("SELECT pg_try_advisory_lock(?)", Boolean.class, key);
        if (!Boolean.TRUE.equals(acquired)) {
            log.debug("Skipping job {}: another instance holds the lock", jobName);
            return false;
        }
        try {
            job.run();
            return true;
        } finally {
            jdbcTemplate.queryForObject("SELECT pg_advisory_unlock(?)", Boolean.class, key);
        }
    }

    /**
     * A stable 64-bit key for a job name.
     *
     * <p>Advisory locks are keyed by number, so the name has to become one, and the mapping must be
     * identical on every instance and across restarts — which rules out {@code String.hashCode()}
     * only by convention and CRC32 not at all. Two job names colliding would make one job block the
     * other; with a handful of jobs whose names we choose, that is a risk worth a comment rather
     * than a lookup table.
     */
    static long lockKey(String jobName) {
        CRC32 crc = new CRC32();
        crc.update(jobName.getBytes(StandardCharsets.UTF_8));
        return crc.getValue();
    }
}
