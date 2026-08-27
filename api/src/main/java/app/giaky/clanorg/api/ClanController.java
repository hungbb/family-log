package app.giaky.clanorg.api;

import app.giaky.clanorg.api.dto.ClanCardResponse;
import app.giaky.clanorg.api.dto.ClanResponse;
import app.giaky.clanorg.api.dto.ClanSummaryResponse;
import app.giaky.clanorg.api.dto.CreateClanRequest;
import app.giaky.clanorg.api.dto.UpdateClanRequest;
import app.giaky.clanorg.application.ClanService;
import app.giaky.clanorg.application.CreateClanCommand;
import app.giaky.clanorg.application.UpdateClanCommand;
import app.giaky.common.security.AuthenticatedUser;
import app.giaky.common.security.CurrentUser;
import app.giaky.common.types.ClanId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
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
 * Clans — doc 05 §4.3.
 *
 * <p>Bind, validate, delegate, map. Nothing here decides whether the caller may do what they are
 * asking: that is {@code InterimClanAccessCheck}, reached through the service, and a check repeated
 * in a controller is a check that will one day disagree with the real one.
 *
 * <p>{@code /clans/lookup} is the one endpoint any signed-in caller reaches without a membership. It
 * answers the minimal card of doc 05 §4.3 and nothing more — see {@code ClanCardResponse}.
 */
@RestController
@RequestMapping("/clans")
@Tag(name = "Clans", description = "Founding, reading and editing a clan (FR-C1…FR-C3, FR-C8)")
@SecurityRequirement(name = "bearerAuth")
public class ClanController {

    private final ClanService clanService;

    public ClanController(ClanService clanService) {
        this.clanService = clanService;
    }

    @PostMapping
    @Operation(
            summary = "Found a clan",
            description =
                    "Creates the clan, its root branch and the founder's membership in one"
                        + " transaction, and returns the generated clan code.")
    public ResponseEntity<ClanResponse> create(
            @CurrentUser AuthenticatedUser caller, @Valid @RequestBody CreateClanRequest request) {

        ClanResponse body =
                ClanResponse.of(
                        clanService.create(
                                caller.id(),
                                new CreateClanCommand(
                                        request.name(),
                                        request.surname(),
                                        request.originPlace(),
                                        request.description())));

        return ResponseEntity.created(URI.create("/api/v1/clans/" + body.id())).body(body);
    }

    @GetMapping
    @Operation(summary = "The clans the caller belongs to", description = "Drives the clan switcher (FR-C8).")
    public List<ClanSummaryResponse> list(@CurrentUser AuthenticatedUser caller) {
        return clanService.listForUser(caller.id()).stream().map(ClanSummaryResponse::of).toList();
    }

    @GetMapping("/lookup")
    @Operation(
            summary = "Preview a clan from a join code",
            description =
                    "Accepts a clan code or a branch code. Returns a deliberately minimal card:"
                        + " enough to recognize the family, nothing worth scraping.")
    public ClanCardResponse lookup(
            @CurrentUser AuthenticatedUser caller,
            @Parameter(example = "NGUYEN-7F3K") @RequestParam("code") String code) {

        return ClanCardResponse.of(clanService.lookupByCode(caller.id(), code));
    }

    @GetMapping("/{clanId}")
    @Operation(
            summary = "A clan",
            description = "404, not 403, when the caller is not a member — doc 04 §7.")
    public ClanResponse detail(
            @CurrentUser AuthenticatedUser caller, @PathVariable UUID clanId) {
        return ClanResponse.of(clanService.detail(caller.id(), ClanId.of(clanId)));
    }

    @PatchMapping("/{clanId}")
    @Operation(
            summary = "Edit a clan",
            description =
                    "Absent fields are left unchanged. The clan code is immutable. Requires the"
                        + " version last read; a mismatch is 409 VERSION_CONFLICT.")
    public ClanResponse update(
            @CurrentUser AuthenticatedUser caller,
            @PathVariable UUID clanId,
            @Valid @RequestBody UpdateClanRequest request) {

        return ClanResponse.of(
                clanService.update(
                        caller.id(),
                        ClanId.of(clanId),
                        new UpdateClanCommand(
                                request.name(),
                                request.description(),
                                request.originPlace(),
                                request.coverMediaId(),
                                request.version())));
    }

    @DeleteMapping("/{clanId}")
    @Operation(summary = "Delete a clan", description = "Soft delete, with a 30-day recovery window.")
    public ResponseEntity<Void> delete(
            @CurrentUser AuthenticatedUser caller, @PathVariable UUID clanId) {

        clanService.softDelete(caller.id(), ClanId.of(clanId));
        return ResponseEntity.noContent().build();
    }
}
