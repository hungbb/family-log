package app.giaky.lineage.api;

import app.giaky.authorization.api.RequiresPermission;
import app.giaky.authorization.domain.Permission;
import app.giaky.common.security.AuthenticatedUser;
import app.giaky.common.security.CurrentUser;
import app.giaky.common.types.ClanId;
import app.giaky.common.types.PersonId;
import app.giaky.lineage.api.dto.AttachChildRequest;
import app.giaky.lineage.api.dto.CreateParentageRequest;
import app.giaky.lineage.api.dto.CreateUnionRequest;
import app.giaky.lineage.api.dto.ParentageResponse;
import app.giaky.lineage.api.dto.PartialDatePayload;
import app.giaky.lineage.api.dto.UnionResponse;
import app.giaky.lineage.api.dto.UpdateUnionRequest;
import app.giaky.lineage.application.AddParentageCommand;
import app.giaky.lineage.application.CreateUnionCommand;
import app.giaky.lineage.application.RelationshipService;
import app.giaky.lineage.application.UpdateUnionCommand;
import app.giaky.lineage.domain.ParentRole;
import app.giaky.lineage.domain.ParentageNature;
import app.giaky.lineage.domain.UnionStatus;
import app.giaky.lineage.domain.UnionType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Relationships — doc 05 §4.8.
 *
 * <p>Every route here is multi-target: the service requires {@code TREE_EDIT} on <i>all</i> the
 * people an edge touches (doc 04 §5.2). The annotation on each method is the usual coarse pre-check
 * and cannot express that rule, which is exactly why the service-level check is the contract.
 */
@RestController
@RequestMapping("/clans/{clanId}")
@Tag(name = "Relationships", description = "Parent-child links and marriages (FR-P4, FR-P5)")
@SecurityRequirement(name = "bearerAuth")
public class RelationshipController {

    private final RelationshipService relationships;

    public RelationshipController(RelationshipService relationships) {
        this.relationships = relationships;
    }

    @PostMapping("/parentages")
    @RequiresPermission(Permission.TREE_EDIT)
    @Operation(
            summary = "Link a parent to a child",
            description =
                    "Needs TREE_EDIT on both people. Answers 422 CYCLE_DETECTED if the chosen parent"
                        + " is already a descendant of the child, and 409 if the child already has a"
                        + " parent in that role.")
    public ResponseEntity<ParentageResponse> addParentage(
            @CurrentUser AuthenticatedUser caller,
            @PathVariable UUID clanId,
            @Valid @RequestBody CreateParentageRequest request) {

        ParentageResponse body =
                ParentageResponse.of(
                        relationships.addParentage(
                                caller.id(),
                                ClanId.of(clanId),
                                new AddParentageCommand(
                                        PersonId.of(request.parentPersonId()),
                                        PersonId.of(request.childPersonId()),
                                        RequestEnums.parse(
                                                ParentRole.class,
                                                request.parentRole(),
                                                "parentRole"),
                                        RequestEnums.parse(
                                                ParentageNature.class, request.nature(), "nature"),
                                        request.unionId())));

        return ResponseEntity.created(
                        URI.create("/api/v1/clans/" + clanId + "/parentages/" + body.id()))
                .body(body);
    }

    @DeleteMapping("/parentages/{parentageId}")
    @RequiresPermission(Permission.TREE_EDIT)
    @Operation(
            summary = "Unlink a parent from a child",
            description = "Needs TREE_EDIT on both endpoints, for the same reason adding one does.")
    public ResponseEntity<Void> removeParentage(
            @CurrentUser AuthenticatedUser caller,
            @PathVariable UUID clanId,
            @PathVariable UUID parentageId) {

        relationships.removeParentage(caller.id(), ClanId.of(clanId), parentageId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/unions")
    @RequiresPermission(Permission.TREE_EDIT)
    @Operation(
            summary = "Record a marriage",
            description =
                    "A PRIMARY union alongside an existing one is refused (I-N3); SECONDARY and"
                        + " SEQUENTIAL are not, because a book with several wives is what this"
                        + " product exists to hold.")
    public ResponseEntity<UnionResponse> createUnion(
            @CurrentUser AuthenticatedUser caller,
            @PathVariable UUID clanId,
            @Valid @RequestBody CreateUnionRequest request) {

        UnionResponse body =
                UnionResponse.of(
                        relationships.createUnion(
                                caller.id(),
                                ClanId.of(clanId),
                                new CreateUnionCommand(
                                        PersonId.of(request.partnerAId()),
                                        PersonId.of(request.partnerBId()),
                                        RequestEnums.parse(
                                                UnionType.class, request.unionType(), "unionType"),
                                        RequestEnums.parse(
                                                UnionStatus.class, request.status(), "status"),
                                        PartialDatePayload.toDomain(
                                                request.startDate(), "startDate"),
                                        PartialDatePayload.toDomain(request.endDate(), "endDate"),
                                        request.notes(),
                                        request.force())));

        return ResponseEntity.created(
                        URI.create("/api/v1/clans/" + clanId + "/unions/" + body.id()))
                .body(body);
    }

    @PatchMapping("/unions/{unionId}")
    @RequiresPermission(Permission.TREE_EDIT)
    @Operation(summary = "Correct a marriage record")
    public UnionResponse updateUnion(
            @CurrentUser AuthenticatedUser caller,
            @PathVariable UUID clanId,
            @PathVariable UUID unionId,
            @Valid @RequestBody UpdateUnionRequest request) {

        return UnionResponse.of(
                relationships.updateUnion(
                        caller.id(),
                        ClanId.of(clanId),
                        unionId,
                        new UpdateUnionCommand(
                                RequestEnums.parse(
                                        UnionType.class, request.unionType(), "unionType"),
                                RequestEnums.parse(UnionStatus.class, request.status(), "status"),
                                PartialDatePayload.toDomain(request.startDate(), "startDate"),
                                PartialDatePayload.toDomain(request.endDate(), "endDate"),
                                request.notes())));
    }

    @DeleteMapping("/unions/{unionId}")
    @RequiresPermission(Permission.TREE_EDIT)
    @Operation(
            summary = "Remove a marriage record",
            description =
                    "Refused with 422 while children are still recorded as belonging to it — they"
                        + " would stay in the tree, but which wife they belong to would be lost.")
    public ResponseEntity<Void> deleteUnion(
            @CurrentUser AuthenticatedUser caller,
            @PathVariable UUID clanId,
            @PathVariable UUID unionId) {

        relationships.deleteUnion(caller.id(), ClanId.of(clanId), unionId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/unions/{unionId}/children")
    @RequiresPermission(Permission.TREE_EDIT)
    @Operation(
            summary = "Attach an existing person as a child of a marriage",
            description =
                    "Needs TREE_EDIT on the child and on both partners. A partner already recorded"
                        + " as this child's parent is skipped rather than refused.")
    public List<ParentageResponse> attachChild(
            @CurrentUser AuthenticatedUser caller,
            @PathVariable UUID clanId,
            @PathVariable UUID unionId,
            @Valid @RequestBody AttachChildRequest request) {

        return relationships
                .attachChildToUnion(
                        caller.id(), ClanId.of(clanId), unionId, PersonId.of(request.personId()))
                .stream()
                .map(ParentageResponse::of)
                .toList();
    }
}
