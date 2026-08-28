package app.giaky.lineage.api;

import app.giaky.authorization.api.RequiresPermission;
import app.giaky.authorization.domain.Permission;
import app.giaky.common.security.AuthenticatedUser;
import app.giaky.common.security.CurrentUser;
import app.giaky.common.types.BranchId;
import app.giaky.common.types.ClanId;
import app.giaky.common.types.Paged;
import app.giaky.common.types.PersonId;
import app.giaky.common.web.PageResponse;
import app.giaky.lineage.api.dto.CreatePersonRequest;
import app.giaky.lineage.api.dto.MovePersonRequest;
import app.giaky.lineage.api.dto.PartialDatePayload;
import app.giaky.lineage.api.dto.PersonDetailResponse;
import app.giaky.lineage.api.dto.PersonSummaryResponse;
import app.giaky.lineage.api.dto.PlacementRequest;
import app.giaky.lineage.api.dto.UpdatePersonRequest;
import app.giaky.lineage.application.CreatePersonCommand;
import app.giaky.lineage.application.PersonService;
import app.giaky.lineage.application.PersonSummaryView;
import app.giaky.lineage.application.UpdatePersonCommand;
import app.giaky.lineage.domain.Gender;
import app.giaky.lineage.domain.LifeStatus;
import app.giaky.lineage.domain.ParentRole;
import app.giaky.lineage.domain.ParentageNature;
import app.giaky.lineage.domain.PersonRepository;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * People — doc 05 §4.7.
 *
 * <p>The clan in the path is authoritative (doc 05 §1): a person id from another clan is a 404 here,
 * never a 403 and never somebody else's ancestor. {@code TenancyBoundaryIT} substitutes one across
 * clans on each of these routes.
 *
 * <p>{@code @RequiresPermission} names no branch on the write routes, deliberately. The URL of
 * {@code PUT /persons/{personId}} says nothing about which branch that person sits in, so the
 * pre-check asks the weakest useful question — "do they hold {@code TREE_EDIT} anywhere in this
 * clan?" — and {@code PersonService} asks the real one against the loaded node. A pre-check that
 * refused something the service would allow is the one thing it must never do.
 */
@RestController
@RequestMapping("/clans/{clanId}/persons")
@Tag(name = "People", description = "The nodes of the family tree (FR-P1…FR-P11)")
@SecurityRequirement(name = "bearerAuth")
public class PersonController {

    private final PersonService personService;

    public PersonController(PersonService personService) {
        this.personService = personService;
    }

    @PostMapping
    @RequiresPermission(Permission.TREE_EDIT)
    @Operation(
            summary = "Add a person",
            description =
                    "The placement decides both the branch the node inherits and the permission"
                        + " checked — a child of somebody in Chi Hai is a question about Chi Hai."
                        + " An UNPLACED node has no branch and therefore needs clan-wide TREE_EDIT.")
    public ResponseEntity<PersonDetailResponse> create(
            @CurrentUser AuthenticatedUser caller,
            @PathVariable UUID clanId,
            @Valid @RequestBody CreatePersonRequest request) {

        PersonDetailResponse body =
                PersonDetailResponse.of(
                        personService.create(
                                caller.id(),
                                ClanId.of(clanId),
                                new CreatePersonCommand(
                                        request.fullName(),
                                        request.familyName(),
                                        request.givenName(),
                                        request.alias(),
                                        request.posthumousName(),
                                        RequestEnums.parse(Gender.class, request.gender(), "gender"),
                                        RequestEnums.parse(
                                                LifeStatus.class,
                                                request.lifeStatus(),
                                                "lifeStatus"),
                                        PartialDatePayload.toDomain(request.birth(), "birth"),
                                        PartialDatePayload.toDomain(request.death(), "death"),
                                        request.burialPlace(),
                                        request.birthOrder(),
                                        request.notesHtml(),
                                        request.foundingAncestor(),
                                        PlacementRequest.toDomain(request.placement()),
                                        RequestEnums.parse(
                                                ParentRole.class,
                                                request.parentRole(),
                                                "parentRole"),
                                        RequestEnums.parse(
                                                ParentageNature.class,
                                                request.nature(),
                                                "nature"))));

        return ResponseEntity.created(
                        URI.create("/api/v1/clans/" + clanId + "/persons/" + body.id()))
                .body(body);
    }

    @GetMapping
    @Operation(
            summary = "Search people",
            description =
                    "Diacritic-insensitive: \"nguyen van an\" finds \"Nguyễn Văn An\". Results are"
                        + " ordered the way a family book is written — by đời, then birth order,"
                        + " then name.")
    public PageResponse<PersonSummaryResponse> search(
            @CurrentUser AuthenticatedUser caller,
            @PathVariable UUID clanId,
            @Parameter(description = "Free text, with or without diacritics") @RequestParam(required = false)
                    String q,
            @RequestParam(required = false) Integer generation,
            @RequestParam(required = false) String lifeStatus,
            @RequestParam(required = false) UUID branchId,
            @Parameter(description = "true for nodes with an account, false for those without")
                    @RequestParam(required = false)
                    Boolean linked,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Paged<PersonSummaryView> results =
                personService.search(
                        caller.id(),
                        ClanId.of(clanId),
                        new PersonRepository.PersonQuery(
                                q,
                                generation,
                                RequestEnums.parse(LifeStatus.class, lifeStatus, "lifeStatus"),
                                branchId == null ? null : BranchId.of(branchId),
                                linked,
                                page,
                                size));

        return new PageResponse<>(
                results.content().stream().map(PersonSummaryResponse::of).toList(),
                results.page(),
                results.size(),
                results.totalElements(),
                results.totalPages());
    }

    /**
     * The restore list (F4.9).
     *
     * <p>Mapped before {@code /{personId}} in this file, though Spring would prefer the literal
     * segment regardless: a reader should not have to know that to be sure {@code /deleted} is not a
     * person id.
     */
    @GetMapping("/deleted")
    @RequiresPermission(Permission.CLAN_ADMIN)
    @Operation(
            summary = "Recently deleted people",
            description = "Soft-deleted within the 30-day recovery window, most recent first.")
    public List<PersonSummaryResponse> recentlyDeleted(
            @CurrentUser AuthenticatedUser caller, @PathVariable UUID clanId) {

        return personService.recentlyDeleted(caller.id(), ClanId.of(clanId)).stream()
                .map(PersonSummaryResponse::of)
                .toList();
    }

    @GetMapping("/{personId}")
    @Operation(
            summary = "One person",
            description = "With parents, marriages, children and siblings (FR-T6).")
    public PersonDetailResponse detail(
            @CurrentUser AuthenticatedUser caller,
            @PathVariable UUID clanId,
            @PathVariable UUID personId) {

        return PersonDetailResponse.of(
                personService.detail(caller.id(), ClanId.of(clanId), PersonId.of(personId)));
    }

    @PutMapping("/{personId}")
    @RequiresPermission(Permission.TREE_EDIT)
    @Operation(
            summary = "Replace a person's details",
            description =
                    "A null field clears it. Requires the version last read; a mismatch is 409"
                        + " VERSION_CONFLICT.")
    public PersonDetailResponse replace(
            @CurrentUser AuthenticatedUser caller,
            @PathVariable UUID clanId,
            @PathVariable UUID personId,
            @Valid @RequestBody UpdatePersonRequest request) {

        return PersonDetailResponse.of(
                personService.replace(
                        caller.id(),
                        ClanId.of(clanId),
                        PersonId.of(personId),
                        toCommand(request)));
    }

    @PatchMapping("/{personId}")
    @RequiresPermission(Permission.TREE_EDIT)
    @Operation(
            summary = "Update some of a person's details",
            description = "A null field is left alone. Requires the version last read.")
    public PersonDetailResponse patch(
            @CurrentUser AuthenticatedUser caller,
            @PathVariable UUID clanId,
            @PathVariable UUID personId,
            @Valid @RequestBody UpdatePersonRequest request) {

        return PersonDetailResponse.of(
                personService.patch(
                        caller.id(),
                        ClanId.of(clanId),
                        PersonId.of(personId),
                        toCommand(request)));
    }

    @DeleteMapping("/{personId}")
    @RequiresPermission(Permission.TREE_EDIT)
    @Operation(
            summary = "Remove a person from the tree",
            description =
                    "Soft delete: the node, its links and its ancestry all stay, so restoring is a"
                        + " timestamp. Refused with 422 if it would leave children with no recorded"
                        + " parent outside the part of the tree you can edit.")
    public ResponseEntity<Void> delete(
            @CurrentUser AuthenticatedUser caller,
            @PathVariable UUID clanId,
            @PathVariable UUID personId) {

        personService.softDelete(caller.id(), ClanId.of(clanId), PersonId.of(personId));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{personId}/restore")
    @RequiresPermission(Permission.CLAN_ADMIN)
    @Operation(summary = "Restore a removed person", description = "Within the 30-day window.")
    public PersonDetailResponse restore(
            @CurrentUser AuthenticatedUser caller,
            @PathVariable UUID clanId,
            @PathVariable UUID personId) {

        return PersonDetailResponse.of(
                personService.restore(caller.id(), ClanId.of(clanId), PersonId.of(personId)));
    }

    @PostMapping("/{personId}/move-branch")
    @RequiresPermission(Permission.TREE_EDIT)
    @Operation(
            summary = "Move a person to another branch",
            description =
                    "Needs TREE_EDIT on the person and on the destination branch (doc 04 §5.2).")
    public PersonDetailResponse moveBranch(
            @CurrentUser AuthenticatedUser caller,
            @PathVariable UUID clanId,
            @PathVariable UUID personId,
            @Valid @RequestBody MovePersonRequest request) {

        return PersonDetailResponse.of(
                personService.moveToBranch(
                        caller.id(),
                        ClanId.of(clanId),
                        PersonId.of(personId),
                        BranchId.of(request.branchId())));
    }

    private static UpdatePersonCommand toCommand(UpdatePersonRequest request) {
        return new UpdatePersonCommand(
                request.fullName(),
                request.familyName(),
                request.givenName(),
                request.alias(),
                request.posthumousName(),
                RequestEnums.parse(Gender.class, request.gender(), "gender"),
                RequestEnums.parse(LifeStatus.class, request.lifeStatus(), "lifeStatus"),
                request.birth() == null
                        ? null
                        : PartialDatePayload.toDomain(request.birth(), "birth"),
                request.death() == null
                        ? null
                        : PartialDatePayload.toDomain(request.death(), "death"),
                request.burialPlace(),
                request.birthOrder(),
                request.notesHtml(),
                request.version());
    }
}
