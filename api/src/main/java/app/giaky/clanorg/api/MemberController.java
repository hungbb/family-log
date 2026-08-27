package app.giaky.clanorg.api;

import app.giaky.clanorg.api.dto.MemberResponse;
import app.giaky.clanorg.application.MembershipService;
import app.giaky.common.security.AuthenticatedUser;
import app.giaky.common.security.CurrentUser;
import app.giaky.common.types.BranchId;
import app.giaky.common.types.ClanId;
import app.giaky.common.types.UserId;
import app.giaky.common.web.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Members of a clan — doc 05 §4.5. */
@RestController
@RequestMapping("/clans/{clanId}/members")
@Tag(name = "Members", description = "Who is in a clan, and the two ways out of one")
@SecurityRequirement(name = "bearerAuth")
@Validated
public class MemberController {

    /** Large enough for a family, small enough that nobody pages the whole table by accident. */
    private static final int MAX_PAGE_SIZE = 100;

    private final MembershipService membershipService;

    public MemberController(MembershipService membershipService) {
        this.membershipService = membershipService;
    }

    @GetMapping
    @Operation(
            summary = "The clan members",
            description = "Filter by name or address with `search`, and by branch with `branchId`.")
    public PageResponse<MemberResponse> list(
            @CurrentUser AuthenticatedUser caller,
            @PathVariable UUID clanId,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) UUID branchId,
            @RequestParam(defaultValue = "0") @PositiveOrZero int page,
            @RequestParam(defaultValue = "20") @Positive @Max(MAX_PAGE_SIZE) int size) {

        var result =
                membershipService.list(
                        caller.id(),
                        ClanId.of(clanId),
                        search,
                        branchId == null ? null : BranchId.of(branchId),
                        page,
                        size);

        return new PageResponse<>(
                result.content().stream().map(MemberResponse::of).toList(),
                result.page(),
                result.size(),
                result.totalElements(),
                result.totalPages());
    }

    @DeleteMapping("/{userId}")
    @Operation(
            summary = "Remove a member",
            description =
                    "Refused with 403 LAST_ADMIN_PROTECTED when it would leave the clan with"
                        + " nobody able to administer it.")
    public ResponseEntity<Void> remove(
            @CurrentUser AuthenticatedUser caller,
            @PathVariable UUID clanId,
            @PathVariable UUID userId) {

        membershipService.remove(caller.id(), ClanId.of(clanId), UserId.of(userId));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/leave")
    @Operation(
            summary = "Leave the clan",
            description =
                    "Subject to the same last-administrator rule: a founder who left would strand"
                        + " the clan, and deleting it is the action they mean.")
    public ResponseEntity<Void> leave(
            @CurrentUser AuthenticatedUser caller, @PathVariable UUID clanId) {

        membershipService.leave(caller.id(), ClanId.of(clanId));
        return ResponseEntity.noContent().build();
    }
}
