package app.giaky.identity.domain;

import app.giaky.common.types.UserId;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A single-use token for confirming an address or resetting a password (FR-A6).
 *
 * <p>Same storage discipline as {@link RefreshToken}: the raw value goes out in a message and only
 * its hash is kept, so the table is useless to anyone who reads it.
 *
 * <p>{@code consumedAt} rather than a revocation: these are used once and then spent. Expiry does
 * the rest — 24 hours for a verification, one hour for a reset, because a reset token is an account
 * takeover waiting in an inbox.
 *
 * @param id the row's identity
 * @param userId whose account it acts on
 * @param purpose what it authorises
 * @param tokenHash SHA-256 of the raw token, hex-encoded
 * @param issuedAt when it was minted
 * @param expiresAt when it stops working
 * @param consumedAt when it was spent; null while unused
 */
public record VerificationToken(
        UUID id,
        UserId userId,
        TokenPurpose purpose,
        String tokenHash,
        Instant issuedAt,
        Instant expiresAt,
        Instant consumedAt) {

    public VerificationToken {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(purpose, "purpose must not be null");
        Objects.requireNonNull(tokenHash, "tokenHash must not be null");
        Objects.requireNonNull(issuedAt, "issuedAt must not be null");
        Objects.requireNonNull(expiresAt, "expiresAt must not be null");
        if (!expiresAt.isAfter(issuedAt)) {
            throw new IllegalArgumentException("expiresAt must be after issuedAt");
        }
    }

    public static VerificationToken issue(
            UUID id,
            UserId userId,
            TokenPurpose purpose,
            String tokenHash,
            Instant issuedAt,
            Instant expiresAt) {
        return new VerificationToken(
                id, userId, purpose, tokenHash, issuedAt, expiresAt, null);
    }

    public boolean isConsumed() {
        return consumedAt != null;
    }

    public boolean isUsableAt(Instant now) {
        return !isConsumed() && now.isBefore(expiresAt);
    }

    /** Spends the token. Consuming a spent token keeps the original timestamp. */
    public VerificationToken consume(Instant now) {
        if (isConsumed()) {
            return this;
        }
        return new VerificationToken(id, userId, purpose, tokenHash, issuedAt, expiresAt, now);
    }
}
