package app.giaky.common.types;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Deterministic {@link IdGenerator} for the {@code test} profile: ids count up from
 * {@code 00000000-0000-0000-0000-000000000001}, so assertions and fixtures can name them.
 *
 * <p>Not for production use — the ids are guessable and collide across JVMs.
 */
public final class SequentialIdGenerator implements IdGenerator {

    private final AtomicLong counter = new AtomicLong();

    @Override
    public UUID newId() {
        return new UUID(0L, counter.incrementAndGet());
    }

    /** Rewinds the sequence so each test starts from a known id. */
    public void reset() {
        counter.set(0L);
    }
}
