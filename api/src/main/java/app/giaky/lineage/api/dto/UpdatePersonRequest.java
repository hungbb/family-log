package app.giaky.lineage.api.dto;

import app.giaky.common.web.NullOrNotBlank;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * Edit a person (doc 05 §4.7).
 *
 * <p>The same body serves {@code PUT} and {@code PATCH}, and the verb decides what a null field
 * means: {@code PUT} clears it, {@code PATCH} leaves it alone. {@code fullName} is therefore not
 * annotated {@code @NotBlank} — a {@code PATCH} that does not mention the name must be allowed to
 * omit it — and the domain refuses a blank one on the {@code PUT} path (I-P1).
 *
 * @param version the version the client last read; a mismatch is {@code 409 VERSION_CONFLICT}
 */
public record UpdatePersonRequest(
        @NullOrNotBlank @Size(max = 200) String fullName,
        @Size(max = 100) String familyName,
        @Size(max = 100) String givenName,
        @Size(max = 100) String alias,
        @Size(max = 100) String posthumousName,
        @Schema(example = "MALE") String gender,
        @Schema(example = "DECEASED") String lifeStatus,
        @Valid PartialDatePayload birth,
        @Valid PartialDatePayload death,
        @Size(max = 255) String burialPlace,
        @Min(1) Integer birthOrder,
        String notesHtml,
        long version) {}
