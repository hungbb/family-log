package app.giaky.clanorg.api;

import app.giaky.clanorg.api.dto.ApproveJoinRequest;
import app.giaky.clanorg.api.dto.JoinRequestResponse;
import app.giaky.clanorg.api.dto.RejectJoinRequest;
import app.giaky.clanorg.application.JoinRequestService;
import app.giaky.clanorg.domain.JoinRequestStatus;
import app.giaky.common.security.AuthenticatedUser;
import app.giaky.common.security.CurrentUser;
import app.giaky.common.types.BranchId;
import app.giaky.common.types.ClanId;
import app.giaky.common.web.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.UUID;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The approver's side of joining — doc 05 §4.5.
 *
 * <p>Split from {@code JoinRequestController} by whose request it is, not by resource. These
 * endpoints are clan-scoped and need a membership; the requester's own are not and cannot be, since
 * an applicant is by definition not a member yet.
 */
@RestController
@RequestMapping("/clans/{clanId}/join-requests")
@Tag(name = "Join requests (inbox)", description = "Answering requests to join a clan (FR-J3)")
@SecurityRequirement(name = "bearerAuth")
@Validated
public class ClanJoinRequestController {

    private static final int MAX_PAGE_SIZE = 100;

    private final JoinRequestService joinRequests;

    public ClanJoinRequestController(JoinRequestService joinRequests) {
        this.joinRequests = joinRequests;
    }

    @GetMapping
    @Operation(
            summary = "The approver inbox",
            description = "Newest first. Omit `status` for every request, decided or not.")
    public PageResponse<JoinRequestResponse> inbox(
            @CurrentUser AuthenticatedUser caller,
            @PathVariable UUID clanId,
            @RequestParam(required = false) JoinRequestStatus status,
            @RequestParam(defaultValue = "0") @PositiveOrZero int page,
            @RequestParam(defaultValue = "20") @Positive @Max(MAX_PAGE_SIZE) int size) {

        var result = joinRequests.inbox(caller.id(), ClanId.of(clanId), status, page, size);
        return new PageResponse<>(
                result.content().stream().map(JoinRequestResponse::of).toList(),
                result.page(),
                result.size(),
                result.totalElements(),
                result.totalPages());
    }

    @PostMapping("/{requestId}/approve")
    @Operation(
            summary = "Approve a request",
            description =
                    "Creates the membership in the same transaction. A second approval of the same"
                        + " request is 409.")
    public JoinRequestResponse approve(
            @CurrentUser AuthenticatedUser caller,
            @PathVariable UUID clanId,
            @PathVariable UUID requestId,
            @RequestBody(required = false) ApproveJoinRequest request) {

        UUID homeBranchId = request == null ? null : request.homeBranchId();
        return JoinRequestResponse.of(
                joinRequests.approve(
                        caller.id(),
                        ClanId.of(clanId),
                        requestId,
                        homeBranchId == null ? null : BranchId.of(homeBranchId)));
    }

    @PostMapping("/{requestId}/reject")
    @Operation(summary = "Refuse a request", description = "The reason is shown to the requester.")
    public JoinRequestResponse reject(
            @CurrentUser AuthenticatedUser caller,
            @PathVariable UUID clanId,
            @PathVariable UUID requestId,
            @Valid @RequestBody RejectJoinRequest request) {

        return JoinRequestResponse.of(
                joinRequests.reject(caller.id(), ClanId.of(clanId), requestId, request.reason()));
    }
}
