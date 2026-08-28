package app.giaky.lineage.api;

import app.giaky.common.security.AuthenticatedUser;
import app.giaky.common.security.CurrentUser;
import app.giaky.common.types.ClanId;
import app.giaky.common.types.PersonId;
import app.giaky.common.web.ETags;
import app.giaky.lineage.api.dto.AncestorResponse;
import app.giaky.lineage.api.dto.TreeResponse;
import app.giaky.lineage.api.dto.TreeStatsResponse;
import app.giaky.lineage.application.TreeQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Reading the tree — doc 05 §4.9.
 *
 * <p><b>The {@code ETag} is checked before the payload is built.</b> The clan's tree version is one
 * indexed read; assembling a five-thousand-person tree is not. Checking {@code If-None-Match}
 * against the version first means an unchanged tree costs a lookup rather than a full read — which
 * is the whole reason B4.12 put a counter on the clan instead of hashing the response body.
 *
 * <p>This is distinct from the {@code ShallowEtagHeaderFilter} in {@code WebConfig}, which also
 * yields a {@code 304} but only after producing the body it then throws away.
 */
@RestController
@RequestMapping("/clans/{clanId}/tree")
@Tag(name = "Tree", description = "The family tree as a flat, client-assembled payload (FR-T1, FR-T2)")
@SecurityRequirement(name = "bearerAuth")
public class TreeController {

    private final TreeQueryService trees;

    public TreeController(TreeQueryService trees) {
        this.trees = trees;
    }

    @GetMapping
    @Operation(
            summary = "The clan tree",
            description =
                    "The master view: every branch, aggregated. Pass rootPersonId to fetch one"
                        + " subtree, and depth to bound it. Send If-None-Match with the ETag from a"
                        + " previous response to get a 304 when nothing has changed.")
    public ResponseEntity<TreeResponse> tree(
            @CurrentUser AuthenticatedUser caller,
            @PathVariable UUID clanId,
            @RequestParam(required = false) UUID rootPersonId,
            @Parameter(description = "Generations below the root; ignored without a root")
                    @RequestParam(required = false)
                    Integer depth,
            @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch) {

        ClanId clan = ClanId.of(clanId);
        long version = trees.treeVersion(caller.id(), clan);
        if (ETags.matches(ifNoneMatch, version)) {
            return ResponseEntity.status(304).eTag(ETags.of(version)).build();
        }

        TreeResponse body =
                TreeResponse.of(
                        trees.tree(
                                caller.id(),
                                clan,
                                rootPersonId == null ? null : PersonId.of(rootPersonId),
                                depth));

        return ResponseEntity.ok().eTag(ETags.of(version)).body(body);
    }

    @GetMapping("/ancestors/{personId}")
    @Operation(
            summary = "A person's ancestors",
            description =
                    "Nearest first, the person themselves at depth 0. More than one line, because"
                        + " parentage is a graph: a father's line and a mother's line are both"
                        + " ancestry.")
    public List<AncestorResponse> ancestors(
            @CurrentUser AuthenticatedUser caller,
            @PathVariable UUID clanId,
            @PathVariable UUID personId) {

        return trees.ancestors(caller.id(), ClanId.of(clanId), PersonId.of(personId)).stream()
                .map(AncestorResponse::of)
                .toList();
    }

    @GetMapping("/descendants/{personId}")
    @Operation(
            summary = "A person's subtree",
            description = "The same payload as the clan tree, bounded to one person and below.")
    public TreeResponse descendants(
            @CurrentUser AuthenticatedUser caller,
            @PathVariable UUID clanId,
            @PathVariable UUID personId,
            @RequestParam(required = false) Integer depth) {

        return TreeResponse.of(
                trees.tree(caller.id(), ClanId.of(clanId), PersonId.of(personId), depth));
    }

    @GetMapping("/stats")
    @Operation(summary = "Counts by generation, life status and branch")
    public TreeStatsResponse stats(
            @CurrentUser AuthenticatedUser caller, @PathVariable UUID clanId) {

        return TreeStatsResponse.of(trees.stats(caller.id(), ClanId.of(clanId)));
    }
}
