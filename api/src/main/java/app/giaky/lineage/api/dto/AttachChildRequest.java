package app.giaky.lineage.api.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Attach an existing person as a child of a marriage (doc 05 §4.8).
 *
 * <p>Writes one edge per partner, both carrying the union id — which is what lets the tree show the
 * children of each wife under the marriage they belong to rather than as one undifferentiated list
 * under the father.
 */
public record AttachChildRequest(@NotNull UUID personId) {}
