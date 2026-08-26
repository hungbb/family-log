package app.giaky.identity.application;

import app.giaky.identity.domain.RefreshToken;
import java.time.Instant;
import java.util.UUID;

/**
 * One live session, as the profile page lists it (task F1.6).
 *
 * <p>No token and no hash — the list is there so a person can recognise a session they do not
 * recognise, and act on it with "log out everywhere". Per-session revocation from this list would
 * need a way to name a session that is not its credential; it arrives if the need does.
 */
public record SessionInfo(
        UUID id, String userAgent, String ipAddress, Instant issuedAt, Instant expiresAt) {

    public static SessionInfo of(RefreshToken token) {
        return new SessionInfo(
                token.id(),
                token.userAgent(),
                token.ipAddress(),
                token.issuedAt(),
                token.expiresAt());
    }
}
