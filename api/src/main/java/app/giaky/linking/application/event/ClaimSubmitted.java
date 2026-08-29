package app.giaky.linking.application.event;

import app.giaky.common.types.BranchId;
import app.giaky.common.types.ClanId;
import app.giaky.common.types.PersonId;
import app.giaky.common.types.UserId;
import java.time.Instant;
import java.util.UUID;

/**
 * Somebody has claimed a node (FR-N1), and the people who can decide it should hear about it.
 *
 * <p>Carries the node's branch as well as its id because routing the notification is a scope
 * question (FR-N2): the subscriber has to ask the resolver which approvers cover <i>this</i> node,
 * and a target cannot be built without the placement.
 *
 * @param branchId null for an unplaced node, which only a clan-scoped approver can reach
 */
public record ClaimSubmitted(
        ClanId clanId,
        UUID claimId,
        PersonId personId,
        BranchId branchId,
        String personName,
        UserId requesterUserId,
        Instant occurredAt) {}
