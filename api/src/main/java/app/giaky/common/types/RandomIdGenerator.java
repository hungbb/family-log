package app.giaky.common.types;

import java.util.UUID;

/** Production {@link IdGenerator}: random UUIDv4. */
public final class RandomIdGenerator implements IdGenerator {

    @Override
    public UUID newId() {
        return UUID.randomUUID();
    }
}
