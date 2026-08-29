package app.giaky.linking.api;

import app.giaky.authorization.api.RequiresPermission;
import app.giaky.authorization.domain.Permission;
import app.giaky.common.security.AuthenticatedUser;
import app.giaky.common.security.CurrentUser;
import app.giaky.common.types.ClanId;
import app.giaky.common.types.PersonId;
import app.giaky.common.web.PageResponse;
import app.giaky.linking.api.dto.NodeClaimResponse;
import app.giaky.linking.api.dto.RejectClaimRequest;
import app.giaky.linking.api.dto.SubmitClaimRequest;
import app.giaky.linking.application.ClaimService;
import app.giaky.linking.application.SubmitClaimCommand;
import app.giaky.linking.domain.ClaimStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Node claiming — doc 05 §4.10.
 *
 * <p>Both audiences on one resource, unlike join requests, and for a reason that is not symmetry:
 * a claimant is <i>already</i> a member of the clan, so every route here can be clan-scoped. The
 * split in {@code JoinRequestController} exists because an applicant is by definition not a member
 * and has no clan-scoped path they are allowed to reach.
 *
 * <p>{@code @RequiresPermission} names no branch on the decision routes, deliberately. The URL says
 * which claim, not which chi the node sits in; asking for the permission at clan scope here would
 * lock out every branch-scoped approver the product has (see the annotation's own note).
 */
@RestController
@RequestMapping("/clans/{clanId}/claims")
@Tag(name = "Node claims", description = "Asking to be recognised as a person in the tree (FR-N1…FR-N3)")
@SecurityRequirement(name = "bearerAuth")
@Validated
public class ClaimController {

    private static final int MAX_PAGE_SIZE = 100;

    private final ClaimService claims;

    public ClaimController(ClaimService claims) {
        this.claims = claims;
    }

    @PostMapping
    @Operation(
            summary = "Claim a person node",
            description =
                    "Open to any active member. `409` when the node is taken, when you already hold"
                        + " a node in this clan, or when you have a live claim on this one.")
    public ResponseEntity<NodeClaimResponse> submit(
            @CurrentUser AuthenticatedUser caller,
            @PathVariable UUID clanId,
            @Valid @RequestBody SubmitClaimRequest request) {

        NodeClaimResponse body =
                NodeClaimResponse.of(
                        claims.submit(
                                caller.id(),
                                ClanId.of(clanId),
                                new SubmitClaimCommand(
                                        PersonId.of(request.personId()), request.evidence())));

        return ResponseEntity.created(
                        URI.create("/api/v1/clans/" + clanId + "/claims/" + body.id()))
                .body(body);
    }

    @DeleteMapping("/{claimId}")
    @Operation(
            summary = "Withdraw your own pending claim",
            description =
                    "Scoped to the caller's own claims: an id belonging to somebody else is a 404,"
                        + " never a 403.")
    public ResponseEntity<Void> cancel(
            @CurrentUser AuthenticatedUser caller,
            @PathVariable UUID clanId,
            @PathVariable UUID claimId) {

        claims.cancel(caller.id(), claimId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    @RequiresPermission(Permission.NODE_CLAIM_APPROVE)
    @Operation(
            summary = "The approver inbox",
            description =
                    "Filtered to the claims this approver may decide (FR-N2). Each row carries the"
                        + " node's parents and children, which is what tells two same-named"
                        + " relatives apart.")
    public PageResponse<NodeClaimResponse> inbox(
            @CurrentUser AuthenticatedUser caller,
            @PathVariable UUID clanId,
            @RequestParam(required = false) ClaimStatus status,
            @RequestParam(defaultValue = "0") @PositiveOrZero int page,
            @RequestParam(defaultValue = "20") @Positive @Max(MAX_PAGE_SIZE) int size) {

        var result = claims.inbox(caller.id(), ClanId.of(clanId), status, page, size);
        return new PageResponse<>(
                result.content().stream().map(NodeClaimResponse::of).toList(),
                result.page(),
                result.size(),
                result.totalElements(),
                result.totalPages());
    }

    @PostMapping("/{claimId}/approve")
    @RequiresPermission(Permission.NODE_CLAIM_APPROVE)
    @Operation(
            summary = "Approve a claim",
            description =
                    "Re-checks that the node is still unlinked and answers `409 NODE_ALREADY_LINKED`"
                        + " if it was taken in the meantime. Grants no edit rights (doc 04 §6).")
    public NodeClaimResponse approve(
            @CurrentUser AuthenticatedUser caller,
            @PathVariable UUID clanId,
            @PathVariable UUID claimId) {

        return NodeClaimResponse.of(claims.approve(caller.id(), ClanId.of(clanId), claimId));
    }

    @PostMapping("/{claimId}/reject")
    @RequiresPermission(Permission.NODE_CLAIM_APPROVE)
    @Operation(summary = "Refuse a claim", description = "The reason is shown to the requester.")
    public NodeClaimResponse reject(
            @CurrentUser AuthenticatedUser caller,
            @PathVariable UUID clanId,
            @PathVariable UUID claimId,
            @Valid @RequestBody RejectClaimRequest request) {

        return NodeClaimResponse.of(
                claims.reject(caller.id(), ClanId.of(clanId), claimId, request.reason()));
    }
}
