package app.giaky.clanorg.application.event;

import app.giaky.common.types.ClanId;
import app.giaky.common.types.UserId;
import java.time.Instant;
import java.util.UUID;

/**
 * A join request left {@code PENDING}, one way or another.
 *
 * <p>One event for four endings rather than four events, because every subscriber so far cares
 * about the same thing — the request is closed — and branches on the outcome only to choose its
 * wording.
 *
 * <p>{@link Outcome} is declared here rather than reused from {@code clanorg.domain}: a subscriber
 * lives in another context, and importing this context's domain is exactly what the layering rules
 * forbid. The duplication is four constants and it is what keeps the event publishable.
 */
public record JoinRequestDecided(
        ClanId clanId,
        UUID requestId,
        UserId requesterUserId,
        Outcome outcome,
        UserId decidedBy,
        Instant occurredAt) {

    /** How a request ended. Mirrors the terminal states of {@code JoinRequestStatus}. */
    public enum Outcome {
        APPROVED,
        REJECTED,
        /** Withdrawn by the requester. */
        CANCELLED,
        /** Closed unanswered by the ninety-day sweep; {@code decidedBy} is null. */
        EXPIRED
    }
}
