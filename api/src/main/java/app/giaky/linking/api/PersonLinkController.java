package app.giaky.linking.api;

import app.giaky.authorization.api.RequiresPermission;
import app.giaky.authorization.domain.Permission;
import app.giaky.common.security.AuthenticatedUser;
import app.giaky.common.security.CurrentUser;
import app.giaky.common.types.BranchId;
import app.giaky.common.types.ClanId;
import app.giaky.common.types.PersonId;
import app.giaky.common.types.UserId;
import app.giaky.common.web.PageResponse;
import app.giaky.linking.api.dto.LinkUserRequest;
import app.giaky.linking.api.dto.LinkedNodeResponse;
import app.giaky.linking.api.dto.PropagationResponse;
import app.giaky.linking.api.dto.SelfProfileRequest;
import app.giaky.linking.api.dto.UnlinkedCandidateResponse;
import app.giaky.linking.application.PersonLinkService;
import app.giaky.linking.application.SelfProfileCommand;
import app.giaky.linking.application.SelfProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The link itself, on the person resource — doc 05 §4.10 (FR-N4, FR-N5, B6.6, B6.7).
 *
 * <p>Shares the {@code /clans/{clanId}/persons} prefix with Lineage's {@code PersonController} and
 * adds no method that collides with one of its. That is the right shape for the URL — a link is a
 * fact about a person — and it keeps the two contexts' code apart, which is what actually matters.
 *
 * <p>{@code /unlinked} is a literal segment and therefore wins over {@code /{personId}} regardless
 * of which controller declares it, exactly as {@code /deleted} already does.
 */
@RestController
@RequestMapping("/clans/{clanId}/persons")
@Tag(name = "Node linking", description = "Attaching an account to a person node (FR-N3…FR-N6)")
@SecurityRequirement(name = "bearerAuth")
@Validated
public class PersonLinkController {

    private final PersonLinkService links;
    private final SelfProfileService selfProfile;

    public PersonLinkController(PersonLinkService links, SelfProfileService selfProfile) {
        this.links = links;
        this.selfProfile = selfProfile;
    }

    @GetMapping("/unlinked")
    @Operation(
            summary = "Nodes nobody has claimed",
            description =
                    "The claiming candidate list (B6.6). Every member may browse it; each row"
                        + " carries the node's parents and children so a person can recognise"
                        + " themselves.")
    public PageResponse<UnlinkedCandidateResponse> unlinked(
            @CurrentUser AuthenticatedUser caller,
            @PathVariable UUID clanId,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Integer generation,
            @RequestParam(required = false) UUID branchId,
            @RequestParam(defaultValue = "0") @PositiveOrZero int page,
            @RequestParam(defaultValue = "20") @Positive @Max(PersonLinkService.MAX_PAGE_SIZE)
                    int size) {

        var result =
                links.unlinkedCandidates(
                        caller.id(),
                        ClanId.of(clanId),
                        q,
                        generation,
                        branchId == null ? null : BranchId.of(branchId),
                        page,
                        size);

        return new PageResponse<>(
                result.content().stream().map(UnlinkedCandidateResponse::of).toList(),
                result.page(),
                result.size(),
                result.totalElements(),
                result.totalPages());
    }

    @GetMapping("/linked-to-me")
    @Operation(
            summary = "The node you are recorded as in this clan",
            description =
                    "`204` when you hold none, which is what the \"find yourself in the tree\""
                        + " banner reads to decide whether to appear (F6.1).")
    public ResponseEntity<LinkedNodeResponse> myNode(
            @CurrentUser AuthenticatedUser caller, @PathVariable UUID clanId) {

        return links.myNode(caller.id(), ClanId.of(clanId))
                .map(LinkedNodeResponse::of)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/{personId}/link-user")
    @Operation(
            summary = "Who is linked to this node",
            description = "`self` is what the tree renders as \"this is you\" (F6.7).")
    public LinkedNodeResponse link(
            @CurrentUser AuthenticatedUser caller,
            @PathVariable UUID clanId,
            @PathVariable UUID personId) {

        return LinkedNodeResponse.of(
                links.linkOf(caller.id(), ClanId.of(clanId), PersonId.of(personId)));
    }

    @PostMapping("/{personId}/link-user")
    @RequiresPermission(Permission.NODE_CLAIM_APPROVE)
    @Operation(
            summary = "Assign a member to this node directly",
            description =
                    "Skips the request flow (FR-N4). The target must be an active member of the"
                        + " clan; `422` if not. Grants no edit rights.")
    public LinkedNodeResponse assign(
            @CurrentUser AuthenticatedUser caller,
            @PathVariable UUID clanId,
            @PathVariable UUID personId,
            @Valid @RequestBody LinkUserRequest request) {

        return LinkedNodeResponse.of(
                links.assign(
                        caller.id(),
                        ClanId.of(clanId),
                        PersonId.of(personId),
                        UserId.of(request.userId())));
    }

    @DeleteMapping("/{personId}/link-user")
    @Operation(
            summary = "Remove the link",
            description =
                    "An approver covering the node, or the linked person themselves (FR-N5). No"
                        + " `@RequiresPermission`: the second of those holds nothing, and the"
                        + " service decides between the two cases.")
    public ResponseEntity<Void> unlink(
            @CurrentUser AuthenticatedUser caller,
            @PathVariable UUID clanId,
            @PathVariable UUID personId) {

        links.unlink(caller.id(), ClanId.of(clanId), PersonId.of(personId));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{personId}/self-profile")
    @Operation(
            summary = "Your propagation choices for your own node",
            description = "`403` unless you are the node's linked user.")
    public PropagationResponse propagation(
            @CurrentUser AuthenticatedUser caller,
            @PathVariable UUID clanId,
            @PathVariable UUID personId) {

        return PropagationResponse.of(
                selfProfile.flags(caller.id(), ClanId.of(clanId), PersonId.of(personId)));
    }

    @PatchMapping("/{personId}/self-profile")
    @Operation(
            summary = "Edit the whitelisted fields on your own node",
            description =
                    "Photo and notes only, without TREE_EDIT (doc 04 §6). Anything else still goes"
                        + " through PUT /persons/{personId} and still needs the permission.")
    public LinkedNodeResponse updateSelfProfile(
            @CurrentUser AuthenticatedUser caller,
            @PathVariable UUID clanId,
            @PathVariable UUID personId,
            @Valid @RequestBody SelfProfileRequest request) {

        return LinkedNodeResponse.of(
                selfProfile.update(
                        caller.id(),
                        ClanId.of(clanId),
                        PersonId.of(personId),
                        new SelfProfileCommand(
                                request.avatarMediaId(),
                                request.notesHtml(),
                                request.propagateAvatar(),
                                request.propagateDisplayName())));
    }
}
