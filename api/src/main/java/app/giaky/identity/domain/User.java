package app.giaky.identity.domain;

import app.giaky.common.types.UserId;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * The account aggregate (doc 02 §2.1).
 *
 * <p>Immutable: every state change returns a new instance, which the application service then
 * saves. That is unusual for a JPA-backed aggregate and deliberate — it makes each transition a
 * named method with a visible result ({@code user = user.verifyEmail(now)}) rather than a setter
 * whose caller may or may not have persisted it, and it lets the whole lifecycle be tested without
 * a database or a mock.
 *
 * <p>Persistence lives in {@code infrastructure} behind a MapStruct mapper, so nothing here knows
 * about JPA (doc 06 §2.1).
 *
 * @param id the account's identity
 * @param email normalized and unique across accounts (I-U1)
 * @param passwordHash never leaves the aggregate (I-U3); no DTO exposes it
 * @param displayName what other members see
 * @param avatarMediaId reference into {@code media_objects}; null until Phase 7
 * @param locale the interface language
 * @param status the lifecycle state; gates authentication (I-U2)
 * @param emailVerifiedAt when the address was confirmed; null while unverified
 * @param lastLoginAt when the account last authenticated; null before the first login
 * @param tokenVersion bumped to invalidate every outstanding access token at once
 * @param createdAt registration time
 * @param updatedAt time of the last change to any of the above
 */
public record User(
        UserId id,
        EmailAddress email,
        PasswordHash passwordHash,
        String displayName,
        UUID avatarMediaId,
        LocaleTag locale,
        UserStatus status,
        Instant emailVerifiedAt,
        Instant lastLoginAt,
        long tokenVersion,
        Instant createdAt,
        Instant updatedAt) {

    /** {@code users.display_name} is {@code VARCHAR(150)}. */
    public static final int DISPLAY_NAME_MAX_LENGTH = 150;

    public User {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(email, "email must not be null");
        Objects.requireNonNull(passwordHash, "passwordHash must not be null");
        Objects.requireNonNull(locale, "locale must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");
        displayName = normalizeDisplayName(displayName);
        if (tokenVersion < 0) {
            throw new IllegalArgumentException("tokenVersion must not be negative");
        }
    }

    /**
     * A newly registered account.
     *
     * <p>Starts {@link UserStatus#PENDING_VERIFICATION}: the address is claimed but unproven, and
     * the difference is visible to the rest of the product from the first moment rather than being
     * back-filled once verification exists.
     */
    public static User register(
            UserId id,
            EmailAddress email,
            PasswordHash passwordHash,
            String displayName,
            LocaleTag locale,
            Instant now) {

        return new User(
                id,
                email,
                passwordHash,
                displayName,
                null,
                locale,
                UserStatus.PENDING_VERIFICATION,
                null,
                null,
                0L,
                now,
                now);
    }

    /** I-U2, delegated so callers never test the status themselves. */
    public boolean canAuthenticate() {
        return status.canAuthenticate();
    }

    public boolean isEmailVerified() {
        return emailVerifiedAt != null;
    }

    /**
     * Confirms the address and promotes the account to {@link UserStatus#ACTIVE}.
     *
     * <p>Idempotent: a second verification returns this instance untouched, so a token replayed by
     * an over-eager email client is not an error worth showing anyone. Only a
     * {@code PENDING_VERIFICATION} account is promoted — verifying an address does not un-suspend
     * an account an operator has suspended.
     */
    public User verifyEmail(Instant now) {
        if (isEmailVerified()) {
            return this;
        }
        UserStatus promoted = status == UserStatus.PENDING_VERIFICATION ? UserStatus.ACTIVE : status;
        return new User(
                id,
                email,
                passwordHash,
                displayName,
                avatarMediaId,
                locale,
                promoted,
                now,
                lastLoginAt,
                tokenVersion,
                createdAt,
                now);
    }

    /** Stamps a successful authentication. */
    public User recordLogin(Instant now) {
        return new User(
                id,
                email,
                passwordHash,
                displayName,
                avatarMediaId,
                locale,
                status,
                emailVerifiedAt,
                now,
                tokenVersion,
                createdAt,
                now);
    }

    /**
     * Replaces the password and invalidates every outstanding session.
     *
     * <p>The {@code tokenVersion} bump is the point, not a side effect: a reset whose reason was "my
     * account is compromised" is worthless if the attacker's access token keeps working for another
     * quarter of an hour.
     */
    public User changePassword(PasswordHash newHash, Instant now) {
        return new User(
                id,
                email,
                newHash,
                displayName,
                avatarMediaId,
                locale,
                status,
                emailVerifiedAt,
                lastLoginAt,
                tokenVersion + 1,
                createdAt,
                now);
    }

    /** "Log out everywhere" (FR-A5): invalidates outstanding access tokens without touching the password. */
    public User revokeAllSessions(Instant now) {
        return new User(
                id,
                email,
                passwordHash,
                displayName,
                avatarMediaId,
                locale,
                status,
                emailVerifiedAt,
                lastLoginAt,
                tokenVersion + 1,
                createdAt,
                now);
    }

    /**
     * Bars the account (I-U2) and invalidates every outstanding access token.
     *
     * <p>The {@code tokenVersion} bump is belt and braces: the filter already refuses a suspended
     * account on its next request, but a bumped version means lifting the suspension later does
     * not bring the old tokens back to life.
     *
     * @throws IllegalStateException the account is already suspended or closed
     */
    public User suspend(Instant now) {
        if (!status.canAuthenticate()) {
            throw new IllegalStateException("only an account that can sign in can be suspended");
        }
        return withStatus(UserStatus.SUSPENDED, tokenVersion + 1, now);
    }

    /**
     * Lifts a suspension.
     *
     * <p>Back to wherever the account would otherwise be: {@code ACTIVE} if the address was
     * verified, {@code PENDING_VERIFICATION} if it never was. Reactivating is not vouching for the
     * address.
     *
     * @throws IllegalStateException the account is not suspended
     */
    public User reactivate(Instant now) {
        if (status != UserStatus.SUSPENDED) {
            throw new IllegalStateException("only a suspended account can be reactivated");
        }
        UserStatus restored =
                isEmailVerified() ? UserStatus.ACTIVE : UserStatus.PENDING_VERIFICATION;
        return withStatus(restored, tokenVersion, now);
    }

    private User withStatus(UserStatus newStatus, long newTokenVersion, Instant now) {
        return new User(
                id,
                email,
                passwordHash,
                displayName,
                avatarMediaId,
                locale,
                newStatus,
                emailVerifiedAt,
                lastLoginAt,
                newTokenVersion,
                createdAt,
                now);
    }

    /**
     * Updates the editable parts of the profile (FR-A4).
     *
     * <p>A null argument means "leave it alone", which is what a {@code PATCH} with an absent field
     * means. Nothing here can be cleared, because neither field has a meaningful empty value.
     */
    public User updateProfile(String newDisplayName, LocaleTag newLocale, Instant now) {
        String resolvedName = newDisplayName == null ? displayName : newDisplayName;
        LocaleTag resolvedLocale = newLocale == null ? locale : newLocale;
        if (resolvedName.equals(displayName) && resolvedLocale == locale) {
            return this;
        }
        return new User(
                id,
                email,
                passwordHash,
                resolvedName,
                avatarMediaId,
                resolvedLocale,
                status,
                emailVerifiedAt,
                lastLoginAt,
                tokenVersion,
                createdAt,
                now);
    }

    private static String normalizeDisplayName(String value) {
        if (value == null) {
            throw new IllegalArgumentException("display name must not be null");
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("display name must not be blank");
        }
        if (trimmed.length() > DISPLAY_NAME_MAX_LENGTH) {
            throw new IllegalArgumentException(
                    "display name must be at most " + DISPLAY_NAME_MAX_LENGTH + " characters");
        }
        return trimmed;
    }
}
