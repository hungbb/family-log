package app.giaky.identity.domain;

import app.giaky.common.types.UserId;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * One refresh-token session (doc 03 §3.2).
 *
 * <p>The raw token exists in the login response and in the client, and nowhere else: what is stored
 * is {@link #tokenHash}, so a database that leaks cannot be replayed as a set of live sessions.
 *
 * <p>{@link #chainId} is the rotation family. Every refresh mints a successor in the same chain, so
 * presenting a token that has already been rotated — the signature of a stolen token being used
 * behind the real client's back — can revoke the whole line in one statement (task B1.4).
 *
 * @param id the row's identity
 * @param userId whose session this is
 * @param chainId the rotation family this token belongs to
 * @param tokenHash SHA-256 of the raw token, hex-encoded
 * @param issuedAt when it was minted
 * @param expiresAt 30 days after issue; a live session rotates long before this
 * @param revokedAt when it stopped being usable; null while live
 * @param revokedReason why; null while live, and always set together with {@code revokedAt}
 * @param userAgent the client that opened the session, for the sessions list
 * @param ipAddress where it was opened from, for the sessions list
 */
public record RefreshToken(
        UUID id,
        UserId userId,
        UUID chainId,
        String tokenHash,
        Instant issuedAt,
        Instant expiresAt,
        Instant revokedAt,
        RevocationReason revokedReason,
        String userAgent,
        String ipAddress) {

    public RefreshToken {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(chainId, "chainId must not be null");
        Objects.requireNonNull(tokenHash, "tokenHash must not be null");
        Objects.requireNonNull(issuedAt, "issuedAt must not be null");
        Objects.requireNonNull(expiresAt, "expiresAt must not be null");
        if ((revokedAt == null) != (revokedReason == null)) {
            throw new IllegalArgumentException(
                    "revokedAt and revokedReason are set together or not at all");
        }
        if (!expiresAt.isAfter(issuedAt)) {
            throw new IllegalArgumentException("expiresAt must be after issuedAt");
        }
    }

    /** Opens a new session — the first token of a fresh chain. */
    public static RefreshToken open(
            UUID id,
            UserId userId,
            String tokenHash,
            Instant issuedAt,
            Instant expiresAt,
            String userAgent,
            String ipAddress) {

        return new RefreshToken(
                id, userId, id, tokenHash, issuedAt, expiresAt, null, null, userAgent, ipAddress);
    }

    /**
     * Continues an existing session: a successor in the same chain, so the two are recognisably one
     * session across every rotation.
     */
    public RefreshToken successor(
            UUID newId, String newTokenHash, Instant issuedAt, Instant expiresAt) {
        return new RefreshToken(
                newId,
                userId,
                chainId,
                newTokenHash,
                issuedAt,
                expiresAt,
                null,
                null,
                userAgent,
                ipAddress);
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public boolean isExpiredAt(Instant now) {
        return !now.isBefore(expiresAt);
    }

    /** Whether this token can still be exchanged for a new pair. */
    public boolean isUsableAt(Instant now) {
        return !isRevoked() && !isExpiredAt(now);
    }

    /** Ends this token's life. Revoking an already-revoked token keeps the original reason. */
    public RefreshToken revoke(RevocationReason reason, Instant now) {
        if (isRevoked()) {
            return this;
        }
        return new RefreshToken(
                id,
                userId,
                chainId,
                tokenHash,
                issuedAt,
                expiresAt,
                now,
                reason,
                userAgent,
                ipAddress);
    }

    /** Why a refresh token stopped being usable. Mirrors the {@code CHECK} on the column. */
    public enum RevocationReason {
        /** The normal end of life: exchanged for a successor. */
        ROTATED,
        /** The holder signed out of this session. */
        LOGOUT,
        /** The holder signed out of every session. */
        LOGOUT_ALL,
        /** A rotated token was presented again; the whole chain went with it. */
        REUSE_DETECTED,
        /** A password reset invalidated every session (FR-A6). */
        PASSWORD_RESET,
        /** A super admin suspended the account; no session may outlive that. */
        SUSPENDED
    }
}
