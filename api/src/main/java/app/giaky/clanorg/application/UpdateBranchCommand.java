package app.giaky.clanorg.application;

/**
 * Rename or re-describe a branch (FR-C5). Structure changes go through the move endpoint.
 *
 * @param expectedVersion the version the client read; a mismatch is a {@code 409 VERSION_CONFLICT}
 */
public record UpdateBranchCommand(String name, String description, long expectedVersion) {}
