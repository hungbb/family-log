package app.giaky.linking.application;

import java.util.UUID;

/**
 * The self-service edit (B6.7, doc 04 §6).
 *
 * <p><b>This record is the whitelist.</b> Not a list checked at runtime — a shape that cannot
 * express anything else. A field that is not here cannot be sent, cannot be bound and cannot be
 * written, and adding one is a visible change to a class whose Javadoc says an ADR is required
 * (phase 6 §8).
 *
 * <p>{@code PUT} semantics, deliberately: a null clears the field. A partial-update shape would need
 * a presence flag per field, and the two fields here are both ones a person edits by looking at the
 * whole form.
 *
 * @param avatarMediaId the media row; Phase 7 uploads these, and until then a client sends null
 * @param notesHtml sanitized server-side before it is stored, by the same sanitizer every other
 *     note write uses
 * @param propagateAvatar FR-N6 opt-in: copy the account photo onto this node from now on
 * @param propagateDisplayName FR-N6 opt-in: copy the account display name onto this node's alias
 */
public record SelfProfileCommand(
        UUID avatarMediaId,
        String notesHtml,
        boolean propagateAvatar,
        boolean propagateDisplayName) {}
