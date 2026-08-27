package app.giaky.common.scheduling;

/**
 * Runs a job on one instance at a time.
 *
 * <p>Every {@code @Scheduled} method in a deployment with two application instances fires twice, at
 * the same moment, against the same rows. For the join-request sweep that is merely wasteful; for
 * the jobs later phases will add it is not. Guarding from the first scheduled job is cheaper than
 * remembering to guard the fourth.
 *
 * <p>A port, because the guarantee belongs to whatever coordinates the instances. The v1 adapter
 * uses a PostgreSQL advisory lock — the deployment already has exactly one thing every instance
 * shares, and it is the database.
 */
public interface SingleRunGuard {

    /**
     * Runs {@code job} if this instance can take the lock named {@code jobName}, and skips it
     * otherwise.
     *
     * <p>Skipping is the correct outcome, not a failure: another instance is doing the work.
     *
     * @return true when the job ran here
     */
    boolean runExclusively(String jobName, Runnable job);
}
