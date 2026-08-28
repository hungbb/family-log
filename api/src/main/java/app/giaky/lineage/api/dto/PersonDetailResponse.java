package app.giaky.lineage.api.dto;

import app.giaky.lineage.application.PersonDetailView;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * One person and the people around them (doc 05 §3.3, FR-T6).
 *
 * <p>The detail drawer's payload. Each related person carries their own {@code canEdit}: a member
 * may well be able to edit the person they opened and not that person's spouse's father.
 *
 * @param version echo this back on a write, or get {@code 409 VERSION_CONFLICT}
 * @param deletedAt set only on a soft-deleted node, which only the restore screen ever sees
 */
@Schema(description = "A person, with parents, marriages, children and siblings.")
public record PersonDetailResponse(
        UUID id,
        UUID clanId,
        UUID branchId,
        String fullName,
        String familyName,
        String givenName,
        String alias,
        String posthumousName,
        String gender,
        String lifeStatus,
        PartialDatePayload birth,
        PartialDatePayload death,
        String burialPlace,
        Integer generation,
        Integer birthOrder,
        UUID avatarMediaId,
        String notesHtml,
        UUID linkedUserId,
        boolean foundingAncestor,
        boolean canEdit,
        List<ParentLinkResponse> parents,
        List<UnionLinkResponse> unions,
        List<PersonSummaryResponse> children,
        List<PersonSummaryResponse> siblings,
        long version,
        Instant createdAt,
        Instant updatedAt,
        Instant deletedAt) {

    /**
     * A parent, and the edge that connects them.
     *
     * @param parentageId what the "remove this parent" action posts to
     */
    public record ParentLinkResponse(
            UUID parentageId, String parentRole, String nature, PersonSummaryResponse person) {}

    /** A marriage, and the other person in it. */
    public record UnionLinkResponse(UnionResponse union, PersonSummaryResponse partner) {}

    public static PersonDetailResponse of(PersonDetailView view) {
        return new PersonDetailResponse(
                view.id(),
                view.clanId(),
                view.branchId(),
                view.fullName(),
                view.familyName(),
                view.givenName(),
                view.alias(),
                view.posthumousName(),
                view.gender().name(),
                view.lifeStatus().name(),
                PartialDatePayload.of(view.birth()),
                PartialDatePayload.of(view.death()),
                view.burialPlace(),
                view.generation(),
                view.birthOrder(),
                view.avatarMediaId(),
                view.notesHtml(),
                view.linkedUserId(),
                view.foundingAncestor(),
                view.canEdit(),
                view.parents().stream()
                        .map(
                                parent ->
                                        new ParentLinkResponse(
                                                parent.parentageId(),
                                                parent.parentRole().name(),
                                                parent.nature().name(),
                                                PersonSummaryResponse.of(parent.person())))
                        .toList(),
                view.unions().stream()
                        .map(
                                union ->
                                        new UnionLinkResponse(
                                                UnionResponse.of(union.union()),
                                                PersonSummaryResponse.of(union.partner())))
                        .toList(),
                view.children().stream().map(PersonSummaryResponse::of).toList(),
                view.siblings().stream().map(PersonSummaryResponse::of).toList(),
                view.version(),
                view.createdAt(),
                view.updatedAt(),
                view.deletedAt());
    }
}
