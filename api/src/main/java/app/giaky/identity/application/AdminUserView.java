package app.giaky.identity.application;

import app.giaky.common.types.UserId;
import app.giaky.identity.domain.LocaleTag;
import app.giaky.identity.domain.User;
import app.giaky.identity.domain.UserStatus;
import java.time.Instant;

/**
 * One account, as the super-admin user list shows it.
 *
 * <p>Like {@link UserProfile}, the point is what is missing: no password hash and no token
 * version. A super admin manages accounts and never needs their credentials.
 *
 * @param superAdmin whether this account is a super admin right now, so the UI can say why it
 *     cannot be suspended
 */
public record AdminUserView(
        UserId id,
        String email,
        String displayName,
        LocaleTag locale,
        UserStatus status,
        boolean emailVerified,
        Instant lastLoginAt,
        Instant createdAt,
        boolean superAdmin) {

    static AdminUserView of(User user, boolean superAdmin) {
        return new AdminUserView(
                user.id(),
                user.email().value(),
                user.displayName(),
                user.locale(),
                user.status(),
                user.isEmailVerified(),
                user.lastLoginAt(),
                user.createdAt(),
                superAdmin);
    }
}
