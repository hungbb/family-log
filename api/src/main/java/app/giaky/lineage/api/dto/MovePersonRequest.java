package app.giaky.lineage.api.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Move a person to another branch (doc 05 §4.7).
 *
 * <p>Needs {@code TREE_EDIT} on the person <i>and</i> on the destination (doc 04 §5.2): moving one
 * of your own nodes into somebody else's chi is an edit to their chi.
 */
public record MovePersonRequest(@NotNull UUID branchId) {}
