package app.giaky.common.types;

import java.util.Objects;
import java.util.UUID;

/**
 * The identity of an account, as a type.
 *
 * <p>This is the <i>only</i> thing other bounded contexts ever hold of a user (doc 02 §1.1). Nothing
 * outside {@code identity} loads a {@code User}: a clan membership, a permission grant and a person
 * node all reference an account by this value and ask Identity when they need more.
 *
 * <p>Wrapping the {@code UUID} is the same defence {@link ClanId} provides — a raw {@code UUID}
 * parameter accepts any id at all, and the compiler cannot tell a user from a person.
 */
public record UserId(UUID value) {

    public UserId {
        Objects.requireNonNull(value, "user id must not be null");
    }

    public static UserId of(UUID value) {
        return new UserId(value);
    }

    public static UserId of(String value) {
        return new UserId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
