package app.giaky.lineage.domain;

import app.giaky.common.types.ClanId;
import app.giaky.common.types.PersonId;
import app.giaky.common.types.UserId;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A parent → child edge (doc 02 §2.6). The structural half of the tree.
 *
 * <p>Everything derived is derived from these: the ancestry closure, generation numbers, subtree
 * scope, the tree render. Which is why the invariants that can be checked on one row are checked
 * here and the two that cannot — I-R4's cycle check and I-R5's cascade — are the job of {@link
 * LineageGraphService}, which can see the rest of the graph.
 *
 * @param unionId the marriage the child was attached through; null when the edge names one parent
 *     alone, which is the common case in an old book
 */
public record Parentage(
        UUID id,
        ClanId clanId,
        PersonId parentPersonId,
        PersonId childPersonId,
        ParentRole parentRole,
        ParentageNature nature,
        UUID unionId,
        UserId createdBy,
        UserId updatedBy,
        Instant createdAt,
        Instant updatedAt,
        Instant deletedAt) {

    public Parentage {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(clanId, "clanId must not be null");
        Objects.requireNonNull(parentPersonId, "parentPersonId must not be null");
        Objects.requireNonNull(childPersonId, "childPersonId must not be null");
        Objects.requireNonNull(parentRole, "parentRole must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");
        nature = nature == null ? ParentageNature.BIOLOGICAL : nature;

        // I-R1, and ck_parentage_not_self in V8. The shortest cycle there is, and the only one a
        // single row can express — every longer one needs the closure to find.
        if (parentPersonId.equals(childPersonId)) {
            throw new IllegalArgumentException("a person cannot be their own parent (I-R1)");
        }
    }

    public static Parentage create(
            UUID id,
            ClanId clanId,
            PersonId parentPersonId,
            PersonId childPersonId,
            ParentRole parentRole,
            ParentageNature nature,
            UUID unionId,
            UserId createdBy,
            Instant now) {

        return new Parentage(
                id,
                clanId,
                parentPersonId,
                childPersonId,
                parentRole,
                nature,
                unionId,
                createdBy,
                createdBy,
                now,
                now,
                null);
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    /**
     * The same edge, no longer naming a marriage. The parent and the child stay linked; only the
     * grouping under one particular union goes — what deleting that union means for an edge.
     */
    public Parentage withoutUnion(UserId actor, Instant now) {
        if (unionId == null) {
            return this;
        }
        return new Parentage(
                id,
                clanId,
                parentPersonId,
                childPersonId,
                parentRole,
                nature,
                null,
                createdBy,
                actor,
                createdAt,
                now,
                deletedAt);
    }

    /**
     * Removes the edge (soft).
     *
     * <p>Soft, so that the audit trail keeps the shape the tree used to have. Whoever repairs a
     * mis-linked branch six months from now is answering "who attached this to that, and when",
     * and a deleted row cannot answer it.
     */
    public Parentage remove(UserId actor, Instant now) {
        if (isDeleted()) {
            return this;
        }
        return new Parentage(
                id,
                clanId,
                parentPersonId,
                childPersonId,
                parentRole,
                nature,
                unionId,
                createdBy,
                actor,
                createdAt,
                now,
                now);
    }
}
