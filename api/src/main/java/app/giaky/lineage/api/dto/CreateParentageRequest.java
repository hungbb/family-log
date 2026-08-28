package app.giaky.lineage.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Link a parent to a child (doc 05 §4.8).
 *
 * <p>Needs {@code TREE_EDIT} on both people (doc 04 §5.2), and answers {@code 422 CYCLE_DETECTED}
 * if the chosen parent already sits below the child.
 *
 * @param parentRole omit to take it from the parent's recorded gender
 * @param unionId the marriage the child was born into, when it is known
 */
public record CreateParentageRequest(
        @NotNull UUID parentPersonId,
        @NotNull UUID childPersonId,
        @Schema(example = "FATHER") String parentRole,
        @Schema(example = "BIOLOGICAL") String nature,
        UUID unionId) {}
