package app.giaky.authorization.application;

import java.util.List;
import java.util.UUID;

/**
 * What the caller may hand out, and where (doc 05 §4.6, F3.4).
 *
 * <p>The grant dialog is built from this rather than from the permission catalogue, and that is the
 * whole idea: a form assembled from what the server says is grantable cannot express an escalation
 * in the first place. The server still refuses one that arrives anyway — the UI is a convenience,
 * never the boundary (doc 07 §8) — but a person who cannot see an option does not spend an afternoon
 * wondering why it does not work.
 *
 * @param subtreeAvailable whether <i>this caller</i> may grant a SUBTREE scope at all — true for
 *     somebody who manages roles clan-wide or over a subtree, false for a branch role manager,
 *     whom {@code grant()} would refuse. Subtree scopes are not listed among the permissions
 *     above: a scope names a person and a clan has thousands of them, so the picker offers a
 *     person search and this flag is what tells it to (F3.6, F4.2)
 */
public record GrantableView(List<GrantablePermission> permissions, boolean subtreeAvailable) {

    public GrantableView {
        permissions = List.copyOf(permissions);
    }

    /**
     * One permission the caller may grant, with every scope they may grant it at.
     *
     * <p>A permission with no grantable scope is left out entirely rather than sent with an empty
     * list: the dialog's first control is "which permission", and offering one that leads to an
     * empty second control is a dead end.
     */
    public record GrantablePermission(String permission, List<GrantableScope> scopes) {

        public GrantablePermission {
            scopes = List.copyOf(scopes);
        }
    }

    /**
     * One place a permission may be granted at.
     *
     * @param scopeRefId null for clan scope
     * @param label the branch name, or the clan's own name for clan scope — what the picker shows
     * @param depth how deep the branch sits, so the picker can indent the tree without re-deriving it
     */
    public record GrantableScope(String scopeType, UUID scopeRefId, String label, int depth) {}
}
