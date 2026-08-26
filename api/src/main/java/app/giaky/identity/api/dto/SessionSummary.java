package app.giaky.identity.api.dto;

import app.giaky.identity.application.SessionInfo;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/**
 * One live session on the profile page (task F1.6).
 *
 * <p>Carries no token and no hash. The list exists so a person can spot a session they do not
 * recognise and act on it with "log out everywhere"; it is not a way to hand out credentials.
 */
@Schema(description = "An active session.")
public record SessionSummary(
        UUID id, String userAgent, String ipAddress, Instant issuedAt, Instant expiresAt) {

    public static SessionSummary of(SessionInfo session) {
        return new SessionSummary(
                session.id(),
                session.userAgent(),
                session.ipAddress(),
                session.issuedAt(),
                session.expiresAt());
    }
}
