package app.giaky.lineage.api.dto;

import app.giaky.common.error.DomainRuleViolation;
import app.giaky.common.error.ErrorCode;
import app.giaky.common.types.BranchId;
import app.giaky.common.types.PersonId;
import app.giaky.lineage.application.PersonPlacement;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import java.util.UUID;

/**
 * Where a new person attaches (doc 05 §4.7).
 *
 * <p>Part of the create request rather than a second call, because placement decides both the
 * branch the node inherits and the permission the server checks — see {@link PersonPlacement}.
 *
 * @param type {@code CHILD_OF_UNION | CHILD_OF_PARENT | SPOUSE_OF | BRANCH_ROOT | UNPLACED}
 * @param unionId the marriage, for {@code CHILD_OF_UNION}
 * @param personId the parent or the spouse, for {@code CHILD_OF_PARENT} and {@code SPOUSE_OF}
 * @param branchId the branch, for {@code BRANCH_ROOT}
 */
@Schema(description = "Where the new person attaches to the tree.")
public record PlacementRequest(
        @NotBlank @Schema(example = "CHILD_OF_PARENT") String type,
        UUID unionId,
        UUID personId,
        UUID branchId) {

    /** Absent placement means {@code UNPLACED}, which the service then requires clan scope for. */
    public static PersonPlacement toDomain(PlacementRequest request) {
        if (request == null) {
            return PersonPlacement.unplaced();
        }
        PersonPlacement.Type type;
        try {
            type = PersonPlacement.Type.valueOf(request.type().trim().toUpperCase());
        } catch (IllegalArgumentException unknown) {
            throw new DomainRuleViolation(
                    ErrorCode.VALIDATION_FAILED, "No such placement: " + request.type());
        }
        return new PersonPlacement(
                type,
                request.unionId(),
                request.personId() == null ? null : PersonId.of(request.personId()),
                request.branchId() == null ? null : BranchId.of(request.branchId()));
    }
}
