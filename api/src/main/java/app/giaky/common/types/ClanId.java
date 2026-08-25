package app.giaky.common.types;

import java.util.Objects;
import java.util.UUID;

/**
 * The tenant boundary, as a type.
 *
 * <p>Every clan-scoped read carries one of these. Passing a raw {@code UUID} around is how a person
 * id ends up where a clan id belongs and one clan reads another's data; the wrapper makes that a
 * compile error. The architecture tests treat "takes a {@code ClanId}" as the definition of a
 * tenant-safe repository method — see {@code architecture.LayeringTest}.
 */
public record ClanId(UUID value) {

    public ClanId {
        Objects.requireNonNull(value, "clan id must not be null");
    }

    public static ClanId of(UUID value) {
        return new ClanId(value);
    }

    public static ClanId of(String value) {
        return new ClanId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
