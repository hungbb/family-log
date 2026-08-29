package app.giaky.identity.application.event;

import app.giaky.common.types.UserId;
import java.time.Instant;
import java.util.UUID;

/**
 * An account changed its display name, locale or photo (FR-A4).
 *
 * <p>Published so that opt-in profile propagation (FR-N6) can copy the changed fields onto the
 * nodes this person is linked to. Identity itself has no interest in the event and no idea that
 * Linking exists, which is the point of publishing it rather than calling anybody.
 *
 * <p>Carries the new values rather than only the id, so a subscriber does not have to read the
 * account back inside the same transaction that just wrote it.
 */
public record ProfileUpdated(
        UserId userId, String displayName, UUID avatarMediaId, Instant occurredAt) {}
