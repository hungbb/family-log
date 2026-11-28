package app.giaky.linking.application.event;

import app.giaky.common.types.ClanId;
import app.giaky.common.types.PersonId;
import app.giaky.common.types.UserId;
import java.time.Instant;
import java.util.UUID;

/**
 * A claim left {@code PENDING}, one way or another.
 *
 * <p>One event for three endings rather than three events, for the same reason {@code
 * JoinRequestDecided} is one: every subscriber cares that the claim is closed and branches on the
 * outcome only to choose its wording.
 *
 * <p>{@link Outcome} is declared here rather than reused from {@code linking.domain}, because a
 * subscriber in another context may not import that package. For the same reason {@code
 * closureCode} is the name of a {@code ClaimClosure} rather than the enum itself.
 *
 * @param reason what an approver typed; null when the system declined it
 * @param closureCode why the system declined it ({@code NODE_TAKEN} or {@code REQUESTER_PLACED}),
 *     for the client to word; null otherwise
 */
public record ClaimDecided(
        ClanId clanId,
        UUID claimId,
        PersonId personId,
        String personName,
        UserId requesterUserId,
        Outcome outcome,
        String reason,
        String closureCode,
        UserId decidedBy,
        Instant occurredAt) {

    /** How a claim ended. Mirrors the terminal states of {@code ClaimStatus}. */
    public enum Outcome {
        APPROVED,
        REJECTED,
        /** Withdrawn by the requester, who therefore needs no notification. */
        CANCELLED
    }
}
