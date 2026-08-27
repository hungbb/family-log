package app.giaky.clanorg.application;

import app.giaky.common.types.BranchId;

/**
 * Add a branch (FR-C4).
 *
 * @param parentBranchId where it hangs; null means directly under the clan's root branch, which is
 *     what the UI means when nobody has picked a parent
 */
public record CreateBranchCommand(BranchId parentBranchId, String name, String description) {}
