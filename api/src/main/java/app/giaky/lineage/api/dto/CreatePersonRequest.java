package app.giaky.lineage.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Add a person (doc 05 §4.7).
 *
 * <p>Only {@code fullName} is required, and that is a product decision rather than laziness: most of
 * what goes into a <i>gia phả</i> starts as a name somebody remembers, and a form that demanded a
 * birth year would be a form that invented one.
 *
 * @param parentRole for a child placement, which parent the anchor is; derived from their gender
 *     when omitted
 * @param nature {@code BIOLOGICAL | ADOPTED | STEP | FOSTER} for a child placement — <i>con nuôi</i>
 *     is recorded rather than flattened
 * @param foundingAncestor claims đời 1; at most one person per clan may (I-P6)
 * @param notesHtml sanitized against an allow-list before it is stored (B4.13)
 */
public record CreatePersonRequest(
        @NotBlank @Size(max = 200) @Schema(example = "Nguyễn Văn An") String fullName,
        @Size(max = 100) String familyName,
        @Size(max = 100) String givenName,
        @Size(max = 100) String alias,
        @Size(max = 100) @Schema(description = "tên thụy") String posthumousName,
        @Schema(example = "MALE", allowableValues = {"MALE", "FEMALE"}) String gender,
        @Schema(example = "DECEASED") String lifeStatus,
        @Valid PartialDatePayload birth,
        @Valid PartialDatePayload death,
        @Size(max = 255) String burialPlace,
        @Min(1) Integer birthOrder,
        String notesHtml,
        boolean foundingAncestor,
        @Valid PlacementRequest placement,
        @Schema(example = "FATHER") String parentRole,
        @Schema(example = "BIOLOGICAL") String nature) {}
