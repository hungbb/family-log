package app.giaky.authorization.application;

import java.util.List;

/**
 * What the caller holds in one clan — {@code GET /clans/{clanId}/my-permissions} (doc 05 §4.3).
 *
 * <p>Two answers in one payload, and they are not the same thing. {@code permissions} is the flat
 * set the UI gates menus on: could this person ever do X here. {@code grants} is the rows behind it,
 * with the granter and the expiry — the permissions <i>screen</i>, and the thing a person reads when
 * they want to know why they can do something, or when it will stop.
 *
 * <p>Neither is a security boundary. Everything listed is re-decided server-side on every request
 * (doc 07 §8); this exists so the interface can be laid out without guessing.
 */
public record MyPermissionsView(List<String> permissions, List<GrantView> grants) {

    public MyPermissionsView {
        permissions = List.copyOf(permissions);
        grants = List.copyOf(grants);
    }
}
