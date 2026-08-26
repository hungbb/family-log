package app.giaky.identity.domain;

import app.giaky.common.types.UserId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Storage for refresh-token sessions. */
public interface RefreshTokenRepository {

    /** Lookup by stored hash — the only way in, since the raw token is never persisted. */
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    RefreshToken save(RefreshToken token);

    /** A user's live sessions, newest first. Drives the profile page's session list. */
    List<RefreshToken> findActiveByUser(UserId userId, Instant now);

    /**
     * Revokes every live token in one rotation family.
     *
     * @return how many were revoked
     */
    int revokeChain(UUID chainId, RefreshToken.RevocationReason reason, Instant now);

    /**
     * Revokes every live token for an account — "log out everywhere", and a password reset.
     *
     * @return how many were revoked
     */
    int revokeAllForUser(UserId userId, RefreshToken.RevocationReason reason, Instant now);
}
