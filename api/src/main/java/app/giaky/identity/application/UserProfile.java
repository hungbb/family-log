package app.giaky.identity.application;

import app.giaky.common.types.UserId;
import app.giaky.identity.domain.LocaleTag;
import app.giaky.identity.domain.User;
import app.giaky.identity.domain.UserStatus;
import java.time.Instant;
import java.util.UUID;

/**
 * An account as the rest of the world may see it.
 *
 * <p>The point of this type is what it leaves out. {@link User} carries a {@code PasswordHash} and a
 * {@code tokenVersion}; neither belongs anywhere near a response, and the way to guarantee that is
 * to have the boundary speak a type that cannot express them (I-U3). {@code SensitiveFieldExposure}
 * asserts it, but the type makes the assertion hard to break in the first place.
 */
public record UserProfile(
        UserId id,
        String email,
        String displayName,
        LocaleTag locale,
        UserStatus status,
        boolean emailVerified,
        Instant emailVerifiedAt,
        UUID avatarMediaId,
        Instant lastLoginAt,
        Instant createdAt) {

    public static UserProfile of(User user) {
        return new UserProfile(
                user.id(),
                user.email().value(),
                user.displayName(),
                user.locale(),
                user.status(),
                user.isEmailVerified(),
                user.emailVerifiedAt(),
                user.avatarMediaId(),
                user.lastLoginAt(),
                user.createdAt());
    }
}
