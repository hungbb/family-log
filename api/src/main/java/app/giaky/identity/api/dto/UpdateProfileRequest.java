package app.giaky.identity.api.dto;

import app.giaky.common.web.NullOrNotBlank;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * A partial update: an absent field is left alone (FR-A4).
 *
 * <p>{@code avatarMediaId} is missing on purpose. Doc 05 §4.2 lists it, but the only way to obtain a
 * media id is the upload endpoint that arrives in Phase 7 — accepting one now would mean validating
 * a reference into a table nothing can populate.
 *
 * @param displayName null leaves it unchanged; blank is rejected
 * @param locale null leaves it unchanged
 */
@Schema(description = "Update the signed-in account's profile (FR-A4).")
public record UpdateProfileRequest(
        @NullOrNotBlank @Size(max = 150) String displayName,
        @Pattern(regexp = LocalePattern.REGEX, message = LocalePattern.MESSAGE) String locale) {}
