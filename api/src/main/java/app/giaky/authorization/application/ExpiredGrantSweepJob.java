package app.giaky.authorization.application;

import app.giaky.common.scheduling.SingleRunGuard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Marks lapsed grants revoked (B3.14).
 *
 * <p><b>Hygiene, not enforcement.</b> Every read path already filters on {@code expires_at}, so a
 * grant stops conferring anything the second it lapses whether or not this job has ever run. What it
 * buys is a permissions table that says what is true: a screen listing grants that expired last
 * March is one somebody eventually acts on.
 *
 * <p>Like {@code JoinRequestExpiryJob}, all this class owns is the timetable and the lock. The work
 * is {@link GrantService#markExpiredRevoked()}, which a test calls directly after moving the clock —
 * a job whose logic can only be reached through a scheduler is a job nobody tests.
 */
@Component
@Profile("!test")
public class ExpiredGrantSweepJob {

    /** The advisory-lock name. Stable: changing it would let two instances run at once, once. */
    static final String JOB_NAME = "authorization.grant-expiry";

    private static final Logger log = LoggerFactory.getLogger(ExpiredGrantSweepJob.class);

    private final GrantService grants;
    private final SingleRunGuard guard;

    public ExpiredGrantSweepJob(GrantService grants, SingleRunGuard guard) {
        this.grants = grants;
        this.guard = guard;
    }

    /** Runs shortly after startup and daily thereafter, on whichever instance takes the lock. */
    @Scheduled(initialDelayString = "PT5M", fixedDelayString = "P1D")
    public void sweep() {
        guard.runExclusively(
                JOB_NAME,
                () -> {
                    int closed = grants.markExpiredRevoked();
                    if (closed > 0) {
                        log.info("Grant expiry sweep closed {} lapsed grants", closed);
                    }
                });
    }
}
