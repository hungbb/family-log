package app.giaky.linking.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * {@code PATCH /clans/{clanId}/persons/{personId}/self-profile} — the self-service exception
 * (B6.7, doc 04 6).
 *
 * <p><b>This record is the whitelist.</b> Two editable fields and no others: a field that is not
 * here cannot be sent, cannot be bound and cannot be written. A name, a date, a parent or a branch
 * still needs {@code TREE_EDIT} and still goes through {@code PUT /persons/{personId}}, which
 * answers {@code 403} to somebody who only holds their own node.
 *
 * <p>Extending it is a visible change to this class, to {@code SelfProfileCommand} and to one SQL
 * statement, and phase 6 section 8 asks for an ADR before it happens.
 */
public record SelfProfileRequest(
        @Schema(description = "The media row for your photo. Phase 7 uploads these.")
                UUID avatarMediaId,
        @Size(max = 20_000) String notesHtml,
        @Schema(description = "FR-N6: keep this node photo in step with your account photo.")
                boolean propagateAvatar,
        @Schema(
                        description =
                                "FR-N6: keep this node alias (ten thuong goi) in step with your"
                                    + " account display name. Never the full name.")
                boolean propagateDisplayName) {}
