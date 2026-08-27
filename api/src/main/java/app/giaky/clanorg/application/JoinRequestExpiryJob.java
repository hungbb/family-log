package app.giaky.clanorg.application;

import app.giaky.common.scheduling.SingleRunGuard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Closes join requests nobody answered (B2.9).
 *
 * <p>All this class owns is the timetable and the guard. The work is {@link
 * JoinRequestService#expireOverdue()}, which a test calls directly after moving the clock — a job
 * whose logic could only be reached through a scheduler is a job nobody tests.
 *
 * <p>Hourly rather than nightly: the requests are ninety days old, so the exact minute is
 * irrelevant, and an hourly job recovers from a restart without anyone thinking about it.
 *
 * <p>Not active under the {@code test} profile. A background thread writing rows while integration
 * tests assert on them is a source of failures that reproduce once a fortnight.
 */
@Component
@Profile("!test")
public class JoinRequestExpiryJob {

    /** The advisory-lock name. Stable: changing it would let two instances run at once once. */
    static final String JOB_NAME = "clanorg.join-request-expiry";

    private static final Logger log = LoggerFactory.getLogger(JoinRequestExpiryJob.class);

    private final JoinRequestService joinRequests;
    private final SingleRunGuard guard;

    public JoinRequestExpiryJob(JoinRequestService joinRequests, SingleRunGuard guard) {
        this.joinRequests = joinRequests;
        this.guard = guard;
    }

    /** Runs an hour after startup and hourly thereafter, on whichever instance takes the lock. */
    @Scheduled(initialDelayString = "PT1H", fixedDelayString = "PT1H")
    public void sweep() {
        guard.runExclusively(
                JOB_NAME,
                () -> {
                    int closed = joinRequests.expireOverdue();
                    if (closed > 0) {
                        log.info("Join-request expiry sweep closed {} requests", closed);
                    }
                });
    }
}
