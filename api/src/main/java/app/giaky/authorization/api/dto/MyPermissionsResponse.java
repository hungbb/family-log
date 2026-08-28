package app.giaky.authorization.api.dto;

import app.giaky.authorization.application.MyPermissionsView;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * {@code GET /clans/{clanId}/my-permissions} — doc 05 §4.3.
 *
 * <p>Two answers, and they are not the same one. {@code permissions} lays out the interface;
 * {@code grants} is the screen a person reads when they want to know <i>why</i> they can do
 * something, and when it stops.
 */
public record MyPermissionsResponse(
        @Schema(
                        description =
                                "What the caller may do here, for laying out the UI. Every one of"
                                    + " these is re-decided server-side on every request.")
                List<String> permissions,
        @Schema(description = "The grants behind that list, with the granter and the expiry.")
                List<GrantResponse> grants) {

    public static MyPermissionsResponse of(MyPermissionsView view) {
        return new MyPermissionsResponse(
                view.permissions(), view.grants().stream().map(GrantResponse::of).toList());
    }
}
