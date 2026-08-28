package app.giaky.authorization.api;

import app.giaky.authorization.api.dto.MyPermissionsResponse;
import app.giaky.authorization.application.PermissionQueryService;
import app.giaky.common.security.AuthenticatedUser;
import app.giaky.common.security.CurrentUser;
import app.giaky.common.types.ClanId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET /clans/{clanId}/my-permissions} — doc 05 §4.3, ADR 0004.
 *
 * <p>Its own controller rather than a method on {@code ClanController}, because the path sits on the
 * clan resource but the answer belongs to Authorization. Putting it in ClanOrg would mean that
 * context importing this one's application service to serve a payload it does not own; a controller
 * here costs one class and keeps the arrow pointing the way doc 02 §7 draws it.
 *
 * <p>No {@code @RequiresPermission}: any member may ask what they themselves hold, and the service
 * answers an empty list for a non-member rather than disclosing that the clan exists.
 */
@RestController
@RequestMapping("/clans/{clanId}/my-permissions")
@Tag(name = "Permissions", description = "Granting and revoking scoped permissions (FR-Z1…FR-Z8)")
@SecurityRequirement(name = "bearerAuth")
public class MyPermissionsController {

    private final PermissionQueryService permissions;

    public MyPermissionsController(PermissionQueryService permissions) {
        this.permissions = permissions;
    }

    @GetMapping
    @Operation(
            summary = "What the caller holds in this clan",
            description =
                    "The flat list the UI gates on, and the grants behind it with the granter and"
                        + " the expiry. Everything listed is re-decided server-side on every"
                        + " request; this is for laying out the interface and for explaining it.")
    public MyPermissionsResponse mine(
            @CurrentUser AuthenticatedUser caller, @PathVariable UUID clanId) {

        return MyPermissionsResponse.of(
                permissions.myPermissions(caller.id(), ClanId.of(clanId)));
    }
}
