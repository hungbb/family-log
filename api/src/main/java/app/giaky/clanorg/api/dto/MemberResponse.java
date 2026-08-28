package app.giaky.clanorg.api.dto;

import app.giaky.clanorg.application.MemberView;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/** One member of a clan (doc 05 §4.5). */
public record MemberResponse(
        UUID userId,
        String displayName,
        String email,
        UUID avatarMediaId,
        UUID homeBranchId,
        String homeBranchName,
        Instant joinedAt,
        @Schema(
                        description =
                                "Whether this member holds CLAN_ADMIN. Any number of people can;"
                                    + " this is who to ask when something is refused.")
                boolean isAdministrator,
        @Schema(description = "The person node this member is linked to. Null until claiming ships.")
                UUID linkedPersonId) {

    public static MemberResponse of(MemberView view) {
        return new MemberResponse(
                view.userId(),
                view.displayName(),
                view.email(),
                view.avatarMediaId(),
                view.homeBranchId(),
                view.homeBranchName(),
                view.joinedAt(),
                view.isAdministrator(),
                view.linkedPersonId());
    }
}
