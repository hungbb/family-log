package app.giaky.linking.api.dto;

import app.giaky.linking.domain.PropagationFlags;

/**
 * Which account fields the caller has agreed to have copied onto their node (FR-N6).
 *
 * <p>Both false unless the person said otherwise, and the form renders them from this rather than
 * from a remembered default.
 */
public record PropagationResponse(boolean propagateAvatar, boolean propagateDisplayName) {

    public static PropagationResponse of(PropagationFlags flags) {
        return new PropagationResponse(flags.avatar(), flags.displayName());
    }
}
