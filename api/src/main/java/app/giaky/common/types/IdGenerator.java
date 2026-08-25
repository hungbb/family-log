package app.giaky.common.types;

import java.util.UUID;

/**
 * Source of identifiers for new aggregates.
 *
 * <p>Injected rather than called statically so the {@code test} profile can hand out a deterministic
 * sequence — see {@code SequentialIdGenerator} and {@code .docs/09-engineering-conventions.md} §4.6.
 */
public interface IdGenerator {

    UUID newId();
}
