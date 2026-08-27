package app.giaky.common.types;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Deterministic {@link IdGenerator} for the {@code test} profile: ids count up from
 * {@code 00000001-0000-0000-0000-000000000001}, so assertions and fixtures can name them.
 *
 * <p>The counter is written into <b>both</b> ends of the UUID, and the leading copy is not
 * decoration. A branch's materialized path is built from the first eight hex characters of its id
 * (doc 02 §3), so ids of the shape {@code 00000000-…-0001} — which is what this generator produced
 * originally — all share one path segment. Every branch in a clan would then claim the same path,
 * {@code ux_branches_path} would refuse the second one, and the failure would appear only under the
 * {@code test} profile, which is the worst possible place for it to appear.
 *
 * <p>Not for production use — the ids are guessable and collide across JVMs.
 */
public final class SequentialIdGenerator implements IdGenerator {

    private final AtomicLong counter = new AtomicLong();

    @Override
    public UUID newId() {
        long next = counter.incrementAndGet();
        // High 32 bits carry the counter, so the eight-character short form is unique too.
        return new UUID(next << 32, next);
    }

    /** Rewinds the sequence so each test starts from a known id. */
    public void reset() {
        counter.set(0L);
    }
}
