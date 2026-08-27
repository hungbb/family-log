package app.giaky.clanorg.application.event;

import app.giaky.common.types.BranchId;
import app.giaky.common.types.ClanId;
import app.giaky.common.types.UserId;
import java.time.Instant;

/** A branch was added. Authorization invalidates its scope cache on this (doc 02 §4). */
public record BranchCreated(
        ClanId clanId, BranchId branchId, BranchId parentBranchId, UserId createdBy, Instant occurredAt) {}
