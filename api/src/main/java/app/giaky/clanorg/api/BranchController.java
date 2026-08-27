package app.giaky.clanorg.api;

import app.giaky.clanorg.api.dto.BranchResponse;
import app.giaky.clanorg.api.dto.CreateBranchRequest;
import app.giaky.clanorg.api.dto.MoveBranchRequest;
import app.giaky.clanorg.api.dto.UpdateBranchRequest;
import app.giaky.clanorg.application.BranchService;
import app.giaky.clanorg.application.CreateBranchCommand;
import app.giaky.clanorg.application.UpdateBranchCommand;
import app.giaky.common.security.AuthenticatedUser;
import app.giaky.common.security.CurrentUser;
import app.giaky.common.types.BranchId;
import app.giaky.common.types.ClanId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Branches — doc 05 §4.4.
 *
 * <p>Every path carries the clan, and the clan in the path is authoritative (doc 05 §1). A branch id
 * from another clan is a 404 here, not a 403 and not somebody else's branch; {@code
 * TenancyBoundaryIT} substitutes one across clans on each of these endpoints.
 */
@RestController
@RequestMapping("/clans/{clanId}/branches")
@Tag(name = "Branches", description = "The branch structure of a clan (FR-C4…FR-C7)")
@SecurityRequirement(name = "bearerAuth")
public class BranchController {

    private final BranchService branchService;

    public BranchController(BranchService branchService) {
        this.branchService = branchService;
    }

    @GetMapping
    @Operation(
            summary = "The branch tree",
            description =
                    "The whole structure, nested from the root branch, with per-branch member"
                        + " counts.")
    public List<BranchResponse> tree(
            @CurrentUser AuthenticatedUser caller, @PathVariable UUID clanId) {

        return branchService.tree(caller.id(), ClanId.of(clanId)).stream()
                .map(BranchResponse::of)
                .toList();
    }

    @PostMapping
    @Operation(
            summary = "Add a branch",
            description = "Omit the parent to add it directly under the clan root branch.")
    public ResponseEntity<BranchResponse> create(
            @CurrentUser AuthenticatedUser caller,
            @PathVariable UUID clanId,
            @Valid @RequestBody CreateBranchRequest request) {

        BranchResponse body =
                BranchResponse.of(
                        branchService.create(
                                caller.id(),
                                ClanId.of(clanId),
                                new CreateBranchCommand(
                                        request.parentBranchId() == null
                                                ? null
                                                : BranchId.of(request.parentBranchId()),
                                        request.name(),
                                        request.description())));

        return ResponseEntity.created(
                        URI.create("/api/v1/clans/" + clanId + "/branches/" + body.id()))
                .body(body);
    }

    @GetMapping("/{branchId}")
    @Operation(summary = "One branch")
    public BranchResponse detail(
            @CurrentUser AuthenticatedUser caller,
            @PathVariable UUID clanId,
            @PathVariable UUID branchId) {

        return BranchResponse.of(
                branchService.detail(caller.id(), ClanId.of(clanId), BranchId.of(branchId)));
    }

    @PatchMapping("/{branchId}")
    @Operation(summary = "Rename or re-describe a branch")
    public BranchResponse update(
            @CurrentUser AuthenticatedUser caller,
            @PathVariable UUID clanId,
            @PathVariable UUID branchId,
            @Valid @RequestBody UpdateBranchRequest request) {

        return BranchResponse.of(
                branchService.update(
                        caller.id(),
                        ClanId.of(clanId),
                        BranchId.of(branchId),
                        new UpdateBranchCommand(
                                request.name(), request.description(), request.version())));
    }

    @PostMapping("/{branchId}/move")
    @Operation(
            summary = "Re-parent a branch",
            description =
                    "Moves the branch and rewrites the materialized path of every branch below it,"
                        + " in one transaction. Refused with 422 CYCLE_DETECTED if the new parent"
                        + " is the branch itself or one of its descendants.")
    public BranchResponse move(
            @CurrentUser AuthenticatedUser caller,
            @PathVariable UUID clanId,
            @PathVariable UUID branchId,
            @Valid @RequestBody MoveBranchRequest request) {

        return BranchResponse.of(
                branchService.move(
                        caller.id(),
                        ClanId.of(clanId),
                        BranchId.of(branchId),
                        BranchId.of(request.newParentBranchId())));
    }

    @PostMapping("/{branchId}/archive")
    @Operation(
            summary = "Archive a branch",
            description = "Refused while it still has branches under it that are not archived.")
    public BranchResponse archive(
            @CurrentUser AuthenticatedUser caller,
            @PathVariable UUID clanId,
            @PathVariable UUID branchId) {

        return BranchResponse.of(
                branchService.archive(caller.id(), ClanId.of(clanId), BranchId.of(branchId)));
    }
}
