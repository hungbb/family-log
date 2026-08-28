package app.giaky.authorization.api;

import app.giaky.authorization.api.dto.CreateGrantRequest;
import app.giaky.authorization.api.dto.GrantResponse;
import app.giaky.authorization.api.dto.GrantableResponse;
import app.giaky.authorization.application.GrantCommand;
import app.giaky.authorization.application.GrantService;
import app.giaky.authorization.domain.Permission;
import app.giaky.common.security.AuthenticatedUser;
import app.giaky.common.security.CurrentUser;
import app.giaky.common.types.ClanId;
import app.giaky.common.types.UserId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Permissions — doc 05 §4.6.
 *
 * <p>Bind, validate, delegate, map. Nothing here decides who may grant what: that is {@code
 * GrantService}, enforcing the four conditions of doc 04 §5.4 through the one resolver, and a check
 * repeated in a controller is a check that will one day disagree with the real one.
 *
 * <p>{@code @RequiresPermission} on the mutating routes is a fast fail for the obvious cases and no
 * more — see the annotation. The service is the contract.
 */
@RestController
@RequestMapping("/clans/{clanId}/permissions")
@Tag(name = "Permissions", description = "Granting and revoking scoped permissions (FR-Z1…FR-Z8)")
@SecurityRequirement(name = "bearerAuth")
public class PermissionController {

    private final GrantService grants;

    public PermissionController(GrantService grants) {
        this.grants = grants;
    }

    @GetMapping
    @Operation(
            summary = "The clan's live grants",
            description =
                    "Visible to anybody who manages roles anywhere in the clan, not only to"
                        + " administrators: somebody who cannot see what is already held cannot"
                        + " tell whether the request in front of them is redundant.")
    public List<GrantResponse> list(
            @CurrentUser AuthenticatedUser caller,
            @PathVariable UUID clanId,
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) String permission) {

        return grants
                .list(
                        caller.id(),
                        ClanId.of(clanId),
                        userId == null ? null : UserId.of(userId),
                        permission)
                .stream()
                .map(GrantResponse::of)
                .toList();
    }

    @GetMapping("/grantable")
    @Operation(
            summary = "What the caller may grant, and where",
            description =
                    "The grant dialog is built from this, so an escalation cannot be expressed in"
                        + " the UI at all. It is still refused server-side if one arrives.")
    public GrantableResponse grantable(
            @CurrentUser AuthenticatedUser caller, @PathVariable UUID clanId) {

        return GrantableResponse.of(grants.grantable(caller.id(), ClanId.of(clanId)));
    }

    @PostMapping
    @RequiresPermission(Permission.ROLE_MANAGE)
    @Operation(
            summary = "Grant a permission",
            description =
                    "Refused with 403 PERMISSION_ESCALATION_BLOCKED unless the caller manages roles"
                        + " over the scope and already holds the permission over it themselves"
                        + " (doc 04 §5.4). A SUBTREE scope names a person and answers 404"
                        + " PERSON_NOT_FOUND if the clan has no such node.")
    public ResponseEntity<GrantResponse> grant(
            @CurrentUser AuthenticatedUser caller,
            @PathVariable UUID clanId,
            @Valid @RequestBody CreateGrantRequest request) {

        GrantResponse body =
                GrantResponse.of(
                        grants.grant(
                                caller.id(),
                                ClanId.of(clanId),
                                new GrantCommand(
                                        UserId.of(request.granteeUserId()),
                                        request.permission(),
                                        request.scopeType(),
                                        request.scopeRefId(),
                                        request.expiresAt())));

        return ResponseEntity.created(
                        URI.create("/api/v1/clans/" + clanId + "/permissions/" + body.id()))
                .body(body);
    }

    @DeleteMapping("/{grantId}")
    @RequiresPermission(Permission.ROLE_MANAGE)
    @Operation(
            summary = "Revoke a permission",
            description =
                    "A soft revoke: the row keeps its history and stops conferring anything."
                        + " Refused with 403 LAST_ADMIN_PROTECTED for the clan's final"
                        + " administrator, including when they are revoking their own.")
    public ResponseEntity<Void> revoke(
            @CurrentUser AuthenticatedUser caller,
            @PathVariable UUID clanId,
            @PathVariable UUID grantId) {

        grants.revoke(caller.id(), ClanId.of(clanId), grantId);
        return ResponseEntity.noContent().build();
    }
}
