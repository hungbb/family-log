package app.giaky.linking.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A claim, from either end (doc 05 §4.10, F6.4, F6.5).
 *
 * <p>One shape for the approver inbox and for the list on {@code /me}, for the same reason {@code
 * JoinRequestView} is one shape: two types would drift, and the difference between the two readers
 * is which fields are populated rather than which fields exist.
 *
 * <p>{@code parentNames} and {@code childNames} are the whole point of the inbox card (F6.5). A
 * <i>gia phả</i> has two Nguyễn Văn Ans of the same <i>đời</i> more often than not, and the thing
 * that tells them apart is whose son each of them was.
 *
 * @param requesterEmail populated only for an approver; on the requester's own list it would be
 *     their own address, and nobody outside the clan ever sees it
 */
public record NodeClaimView(
        UUID id,
        UUID clanId,
        String clanName,
        UUID personId,
        String personName,
        Integer personGeneration,
        List<String> parentNames,
        List<String> childNames,
        UUID requesterUserId,
        String requesterName,
        String requesterEmail,
        String evidence,
        String status,
        UUID decidedBy,
        String decidedByName,
        Instant decidedAt,
        String decisionReason,
        Instant createdAt) {}
