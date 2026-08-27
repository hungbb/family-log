package app.giaky.clanorg.application;

import app.giaky.clanorg.domain.Clan;
import app.giaky.clanorg.domain.ClanMembership;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * One row of the clans a caller belongs to — what the clan switcher renders (FR-C8).
 *
 * <p>Carries the caller's own membership alongside the clan, because the switcher needs both and
 * fetching them separately would be two round trips for one control.
 */
public record ClanSummaryView(
        UUID id,
        String clanCode,
        String name,
        String originPlace,
        UUID coverMediaId,
        UUID rootBranchId,
        UUID homeBranchId,
        Instant joinedAt,
        List<String> myPermissions) {

    static ClanSummaryView of(Clan clan, ClanMembership membership, List<String> myPermissions) {
        return new ClanSummaryView(
                clan.id().value(),
                clan.clanCode().value(),
                clan.name(),
                clan.originPlace(),
                clan.coverMediaId(),
                clan.rootBranchId() == null ? null : clan.rootBranchId().value(),
                membership.homeBranchId() == null ? null : membership.homeBranchId().value(),
                membership.joinedAt(),
                List.copyOf(myPermissions));
    }
}
