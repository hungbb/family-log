package app.giaky.clanorg.api.dto;

import app.giaky.clanorg.application.ClanSummaryView;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** One row of {@code GET /clans} — what the clan switcher renders (FR-C8). */
public record ClanSummaryResponse(
        UUID id,
        String clanCode,
        String name,
        String originPlace,
        UUID coverMediaId,
        UUID rootBranchId,
        UUID homeBranchId,
        Instant joinedAt,
        List<String> myPermissions) {

    public static ClanSummaryResponse of(ClanSummaryView view) {
        return new ClanSummaryResponse(
                view.id(),
                view.clanCode(),
                view.name(),
                view.originPlace(),
                view.coverMediaId(),
                view.rootBranchId(),
                view.homeBranchId(),
                view.joinedAt(),
                view.myPermissions());
    }
}
