package app.giaky.clanorg.application;

import app.giaky.clanorg.domain.Clan;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A clan as its members see it (doc 05 §4.3).
 *
 * <p>Built from primitives rather than from domain types, so a controller can serialize it without
 * knowing that {@code ClanCode} or {@code ClanId} exist. That is the same reason {@code UserProfile}
 * is shaped the way it is.
 *
 * @param myPermissions what the caller may do here, for UI gating only — the server re-decides every
 *     one of them on every request (doc 07 §8)
 * @param version echoed back on a PATCH; a mismatch is a 409
 */
public record ClanView(
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
        List<String> myPermissions,
        long version,
        Instant createdAt,
        Instant updatedAt) {

    static ClanView of(Clan clan, long memberCount, long branchCount, List<String> myPermissions) {
        return new ClanView(
                clan.id().value(),
                clan.clanCode().value(),
                clan.name(),
                clan.description(),
                clan.originPlace(),
                clan.coverMediaId(),
                clan.visibility().name(),
                clan.rootBranchId() == null ? null : clan.rootBranchId().value(),
                memberCount,
                branchCount,
                List.copyOf(myPermissions),
                clan.version(),
                clan.createdAt(),
                clan.updatedAt());
    }
}
