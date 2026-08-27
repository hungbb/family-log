package app.giaky.clanorg.api.dto;

import app.giaky.clanorg.application.ClanView;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A clan, to a member (doc 05 §4.3).
 *
 * <p>A separate type from {@code ClanView} even though the two currently agree field for field. The
 * view is what a use case returns; this is the published contract. Keeping them apart is what lets
 * one change without the other having to — and doc 05 §6 treats a change to this one as a versioning
 * question.
 */
public record ClanResponse(
        UUID id,
        String clanCode,
        String name,
        String description,
        String originPlace,
        UUID coverMediaId,
        String visibility,
        UUID rootBranchId,
        long memberCount,
        long branchCount,
        @Schema(
                        description =
                                "What the caller may do here. For laying out the UI only — every"
                                    + " one of these is re-decided server-side on every request.")
                List<String> myPermissions,
        long version,
        Instant createdAt,
        Instant updatedAt) {

    public static ClanResponse of(ClanView view) {
        return new ClanResponse(
                view.id(),
                view.clanCode(),
                view.name(),
                view.description(),
                view.originPlace(),
                view.coverMediaId(),
                view.visibility(),
                view.rootBranchId(),
                view.memberCount(),
                view.branchCount(),
                view.myPermissions(),
                view.version(),
                view.createdAt(),
                view.updatedAt());
    }
}
