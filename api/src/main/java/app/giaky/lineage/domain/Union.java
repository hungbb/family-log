package app.giaky.lineage.domain;

import app.giaky.common.types.ClanId;
import app.giaky.common.types.PartialDate;
import app.giaky.common.types.PersonId;
import app.giaky.common.types.UserId;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A marriage or partnership between two people (doc 02 §2.7).
 *
 * <p><b>The pair is unordered and stored ordered.</b> A marriage has no first partner, but "at most
 * one union per pair" (I-N2) is a unique index only if the pair has a canonical form — so {@link
 * #create} sorts the two ids and the constructor refuses anything else. Everything downstream reads
 * {@code partnerA}/{@code partnerB} as an implementation detail and never as a role; the UI decides
 * who to show first from gender and dates, not from this.
 *
 * <p>"Sorted" means sorted the way the database sorts them — see {@link #precedes}.
 *
 * <p>Unions carry no structural weight. Removing one does not detach anybody from the tree: children
 * hang from {@link Parentage} edges, which merely <i>reference</i> the union they were attached
 * through. That separation is what lets a family correct a marriage record without the tree moving.
 */
public record Union(
        UUID id,
        ClanId clanId,
        PersonId partnerAId,
        PersonId partnerBId,
        UnionType unionType,
        UnionStatus status,
        PartialDate startDate,
        PartialDate endDate,
        String notes,
        UserId createdBy,
        UserId updatedBy,
        Instant createdAt,
        Instant updatedAt,
        Instant deletedAt) {

    public static final int NOTES_MAX_LENGTH = 2_000;

    public Union {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(clanId, "clanId must not be null");
        Objects.requireNonNull(partnerAId, "partnerAId must not be null");
        Objects.requireNonNull(partnerBId, "partnerBId must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");

        unionType = unionType == null ? UnionType.PRIMARY : unionType;
        status = status == null ? UnionStatus.UNKNOWN : status;
        startDate = startDate == null ? PartialDate.unknown() : startDate;
        endDate = endDate == null ? PartialDate.unknown() : endDate;
        notes = Person.trimToNull(notes, NOTES_MAX_LENGTH, "notes");

        // I-N1.
        if (partnerAId.equals(partnerBId)) {
            throw new IllegalArgumentException("a union needs two different people (I-N1)");
        }
        // The canonical ordering ck_union_canonical_order depends on. Enforced rather than applied
        // here so that a caller who builds one by hand cannot create the second row for a pair.
        if (!precedes(partnerAId, partnerBId)) {
            throw new IllegalArgumentException(
                    "partners must be stored in canonical order; use Union.create");
        }
        if (startDate.isCertainlyAfter(endDate)) {
            throw new IllegalArgumentException("a union cannot end before it began");
        }
    }

    /** Creates a union from an unordered pair, putting the two partners in canonical order. */
    public static Union create(
            UUID id,
            ClanId clanId,
            PersonId onePartner,
            PersonId otherPartner,
            UnionType unionType,
            UnionStatus status,
            PartialDate startDate,
            PartialDate endDate,
            String notes,
            UserId createdBy,
            Instant now) {

        Objects.requireNonNull(onePartner, "onePartner must not be null");
        Objects.requireNonNull(otherPartner, "otherPartner must not be null");
        boolean ordered = precedes(onePartner, otherPartner);

        return new Union(
                id,
                clanId,
                ordered ? onePartner : otherPartner,
                ordered ? otherPartner : onePartner,
                unionType,
                status,
                startDate,
                endDate,
                notes,
                createdBy,
                createdBy,
                now,
                now,
                null);
    }

    /**
     * Whether {@code one} is stored before {@code other}: the order PostgreSQL gives two {@code
     * uuid} values, which is the order {@code ck_union_canonical_order} is written in.
     *
     * <p><b>Not {@link UUID#compareTo}.</b> That compares each half as a <i>signed</i> long, so every
     * id whose first hex digit is 8–f sorts before every id that starts 0–7. PostgreSQL compares the
     * sixteen bytes unsigned. The two orders agree unless exactly one of the ids has its top bit
     * set — which, for random ids, is half of all pairs, and every one of those was a marriage the
     * check constraint refused with a 500. The sequential ids of the {@code test} profile never set
     * that bit, which is why no test saw it.
     *
     * <p>Everything that needs the canonical form of a pair asks here, so there is one definition
     * of it and it is the database's.
     */
    public static boolean precedes(PersonId one, PersonId other) {
        UUID left = one.value();
        UUID right = other.value();
        int high =
                Long.compareUnsigned(
                        left.getMostSignificantBits(), right.getMostSignificantBits());
        int order =
                high != 0
                        ? high
                        : Long.compareUnsigned(
                                left.getLeastSignificantBits(), right.getLeastSignificantBits());
        return order < 0;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public boolean involves(PersonId personId) {
        return partnerAId.equals(personId) || partnerBId.equals(personId);
    }

    /** The other person in this union, given one of them. */
    public PersonId partnerOf(PersonId personId) {
        if (partnerAId.equals(personId)) {
            return partnerBId;
        }
        if (partnerBId.equals(personId)) {
            return partnerAId;
        }
        throw new IllegalArgumentException(personId + " is not part of this union");
    }

    /** I-N3's subject: a live principal marriage. */
    public boolean isActivePrimary() {
        return !isDeleted()
                && unionType == UnionType.PRIMARY
                && (status == UnionStatus.ACTIVE || status == UnionStatus.UNKNOWN);
    }

    public Union update(
            UnionType newType,
            UnionStatus newStatus,
            PartialDate newStart,
            PartialDate newEnd,
            String newNotes,
            UserId editor,
            Instant now) {

        return new Union(
                id,
                clanId,
                partnerAId,
                partnerBId,
                newType,
                newStatus,
                newStart,
                newEnd,
                newNotes,
                createdBy,
                editor,
                createdAt,
                now,
                deletedAt);
    }

    /** Soft removal, for the same reason {@link Parentage#remove} is soft. */
    public Union remove(UserId actor, Instant now) {
        if (isDeleted()) {
            return this;
        }
        return new Union(
                id,
                clanId,
                partnerAId,
                partnerBId,
                unionType,
                status,
                startDate,
                endDate,
                notes,
                createdBy,
                actor,
                createdAt,
                now,
                now);
    }
}
