package app.giaky.lineage.application;

import app.giaky.common.types.PartialDate;
import app.giaky.lineage.domain.Gender;
import app.giaky.lineage.domain.LifeStatus;
import app.giaky.lineage.domain.Person;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * One person, with the people around them (doc 05 §3.3, FR-T6).
 *
 * <p>The detail drawer's payload, and deliberately not the tree node's: parents, unions, children
 * and siblings are four more queries, which is fine once and ruinous per node.
 *
 * <p>Relations are {@link PersonSummaryView}s so the drawer can render a clickable card for each
 * without a second round trip, and each carries its own {@code canEdit} — a member may well be able
 * to edit the person they opened and not their spouse's father.
 *
 * @param parents at most two, one per {@code ParentRole} (I-R3)
 * @param version the optimistic-locking version the client must echo back on a write (doc 05 §1)
 */
public record PersonDetailView(
        UUID id,
        UUID clanId,
        UUID branchId,
        String fullName,
        String familyName,
        String givenName,
        String alias,
        String posthumousName,
        Gender gender,
        LifeStatus lifeStatus,
        PartialDate birth,
        PartialDate death,
        String burialPlace,
        Integer generation,
        Integer birthOrder,
        UUID avatarMediaId,
        String notesHtml,
        UUID linkedUserId,
        boolean foundingAncestor,
        boolean canEdit,
        List<ParentLink> parents,
        List<UnionLink> unions,
        List<PersonSummaryView> children,
        List<PersonSummaryView> siblings,
        long version,
        Instant createdAt,
        Instant updatedAt,
        Instant deletedAt) {

    /**
     * A parent, and the edge that connects them.
     *
     * @param parentageId the edge's own id — what the "remove this parent" button posts to
     */
    public record ParentLink(
            UUID parentageId,
            app.giaky.lineage.domain.ParentRole parentRole,
            app.giaky.lineage.domain.ParentageNature nature,
            PersonSummaryView person) {}

    /** A marriage, and the other person in it. */
    public record UnionLink(UnionView union, PersonSummaryView partner) {}

    static PersonDetailView of(
            Person person,
            boolean canEdit,
            List<ParentLink> parents,
            List<UnionLink> unions,
            List<PersonSummaryView> children,
            List<PersonSummaryView> siblings) {

        return new PersonDetailView(
                person.id().value(),
                person.clanId().value(),
                person.branchId() == null ? null : person.branchId().value(),
                person.fullName(),
                person.name().familyName(),
                person.name().givenName(),
                person.name().alias(),
                person.name().posthumousName(),
                person.gender(),
                person.lifeStatus(),
                person.birth(),
                person.death(),
                person.burialPlace(),
                person.generation(),
                person.birthOrder(),
                person.avatarMediaId(),
                person.notesHtml(),
                person.linkedUserId() == null ? null : person.linkedUserId().value(),
                person.foundingAncestor(),
                canEdit,
                List.copyOf(parents),
                List.copyOf(unions),
                List.copyOf(children),
                List.copyOf(siblings),
                person.version(),
                person.createdAt(),
                person.updatedAt(),
                person.deletedAt());
    }
}
