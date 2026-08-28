package app.giaky.authorization.api.dto;

import app.giaky.authorization.application.GrantableView;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

/**
 * {@code GET /clans/{clanId}/permissions/grantable} — the grant dialog's whole vocabulary (F3.4).
 *
 * <p>The client builds its form from this and nothing else, so an escalation is not something the
 * UI declines to offer: it is something the UI has no way to express. The server refuses one that
 * arrives anyway (doc 04 §5.4) — this is ergonomics, not the boundary.
 */
public record GrantableResponse(
        List<GrantablePermissionResponse> permissions,
        @Schema(
                        description =
                                "False until the family tree exists. The scope picker shows the"
                                    + " subtree option disabled rather than hiding it.")
                boolean subtreeAvailable) {

    public static GrantableResponse of(GrantableView view) {
        return new GrantableResponse(
                view.permissions().stream().map(GrantablePermissionResponse::of).toList(),
                view.subtreeAvailable());
    }

    /** One permission the caller may grant, with the scopes they may grant it at. */
    public record GrantablePermissionResponse(String permission, List<GrantableScopeResponse> scopes) {

        static GrantablePermissionResponse of(GrantableView.GrantablePermission permission) {
            return new GrantablePermissionResponse(
                    permission.permission(),
                    permission.scopes().stream().map(GrantableScopeResponse::of).toList());
        }
    }

    /**
     * One place a permission may be granted at.
     *
     * @param depth how deep the branch sits, so the picker indents the tree without re-deriving it
     */
    public record GrantableScopeResponse(
            String scopeType, UUID scopeRefId, String label, int depth) {

        static GrantableScopeResponse of(GrantableView.GrantableScope scope) {
            return new GrantableScopeResponse(
                    scope.scopeType(), scope.scopeRefId(), scope.label(), scope.depth());
        }
    }
}
