package app.giaky.lineage.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.giaky.common.types.ClanId;
import app.giaky.common.types.PartialDate;
import app.giaky.common.types.PersonId;
import app.giaky.common.types.UserId;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * {@link Parentage} and {@link Union} — the invariants a single edge can be held to.
 *
 * <p>I-R3 (one parent per role), I-R4 (no cycles), I-N2 (one union per pair) and I-N3 (one live
 * primary) need to see more than one row and are tested where they are enforced, in {@code
 * LineageEdgesTest} and {@code LineageGraphServiceTest}.
 */
class RelationshipInvariantTest {

    private static final ClanId CLAN = ClanId.of(UUID.randomUUID());
    private static final UserId ACTOR = UserId.of(UUID.randomUUID());
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Nested
    @DisplayName("Parentage")
    class Parentages {

        @Test
        @DisplayName("I-R1: nobody is their own parent")
        void notSelfParent() {
            PersonId person = PersonId.of(UUID.randomUUID());

            assertThatThrownBy(
                            () ->
                                    Parentage.create(
                                            UUID.randomUUID(),
                                            CLAN,
                                            person,
                                            person,
                                            ParentRole.FATHER,
                                            ParentageNature.BIOLOGICAL,
                                            null,
                                            ACTOR,
                                            NOW))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("I-R1");
        }

        @Test
        @DisplayName("nature defaults to biological, which is what a book usually means")
        void natureDefaults() {
            Parentage edge = edge(ParentRole.FATHER, null);

            assertThat(edge.nature()).isEqualTo(ParentageNature.BIOLOGICAL);
        }

        @Test
        @DisplayName("removal is soft, so the audit trail keeps the shape the tree used to have")
        void removalIsSoft() {
            Parentage edge = edge(ParentRole.FATHER, ParentageNature.ADOPTED);
            Parentage removed = edge.remove(ACTOR, NOW.plusSeconds(60));

            assertThat(removed.isDeleted()).isTrue();
            assertThat(removed.nature()).isEqualTo(ParentageNature.ADOPTED);
            assertThat(removed.remove(ACTOR, NOW.plusSeconds(120))).isSameAs(removed);
        }

        private Parentage edge(ParentRole role, ParentageNature nature) {
            return Parentage.create(
                    UUID.randomUUID(),
                    CLAN,
                    PersonId.of(UUID.randomUUID()),
                    PersonId.of(UUID.randomUUID()),
                    role,
                    nature,
                    null,
                    ACTOR,
                    NOW);
        }
    }

    @Nested
    @DisplayName("Union")
    class Unions {

        @Test
        @DisplayName("I-N1: a union needs two different people")
        void twoDifferentPeople() {
            PersonId person = PersonId.of(UUID.randomUUID());

            assertThatThrownBy(() -> union(person, person))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("I-N1");
        }

        @Test
        @DisplayName("the pair is stored in canonical order whichever way it arrives")
        void canonicalOrder() {
            PersonId one = PersonId.of(UUID.randomUUID());
            PersonId other = PersonId.of(UUID.randomUUID());

            Union forwards = union(one, other);
            Union backwards = union(other, one);

            assertThat(forwards.partnerAId()).isEqualTo(backwards.partnerAId());
            assertThat(forwards.partnerBId()).isEqualTo(backwards.partnerBId());
            assertThat(Union.precedes(forwards.partnerAId(), forwards.partnerBId()))
                    .as("the ordering ux_union_pair depends on")
                    .isTrue();
        }

        @Test
        @DisplayName("canonical order is PostgreSQL's order of the two ids, not java.util.UUID's")
        void canonicalOrderIsTheDatabases() {
            // One id either side of the sign bit: the only pairs on which UUID.compareTo, which
            // reads each half as a signed long, disagrees with PostgreSQL's byte-wise comparison.
            // Random ids land here half the time; the sequential ids of the test profile never do.
            PersonId belowTheSignBit =
                    PersonId.of(UUID.fromString("7fffffff-ffff-4fff-bfff-ffffffffffff"));
            PersonId aboveTheSignBit =
                    PersonId.of(UUID.fromString("80000000-0000-4000-8000-000000000000"));

            Union forwards = union(belowTheSignBit, aboveTheSignBit);
            Union backwards = union(aboveTheSignBit, belowTheSignBit);

            assertThat(forwards.partnerAId())
                    .as("7f… sorts before 80… in PostgreSQL, and ck_union_canonical_order is its")
                    .isEqualTo(belowTheSignBit);
            assertThat(forwards.partnerBId()).isEqualTo(aboveTheSignBit);
            assertThat(backwards.partnerAId()).isEqualTo(belowTheSignBit);
            assertThat(backwards.partnerBId()).isEqualTo(aboveTheSignBit);
        }

        @Test
        @DisplayName("the low half of an id is compared unsigned as well")
        void canonicalOrderInTheLowHalf() {
            PersonId smaller = PersonId.of(new UUID(7L, 0x7fff_ffff_ffff_ffffL));
            PersonId larger = PersonId.of(new UUID(7L, 0x8000_0000_0000_0000L));

            assertThat(Union.precedes(smaller, larger)).isTrue();
            assertThat(Union.precedes(larger, smaller)).isFalse();
            assertThat(Union.precedes(smaller, smaller))
                    .as("an id does not precede itself — I-N1 refuses the pair before this matters")
                    .isFalse();
            assertThat(union(larger, smaller).partnerAId()).isEqualTo(smaller);
        }

        @Test
        @DisplayName("the constructor refuses a hand-built pair in the wrong order")
        void constructorEnforcesOrder() {
            PersonId one = PersonId.of(UUID.randomUUID());
            PersonId other = PersonId.of(UUID.randomUUID());
            boolean oneIsSmaller = Union.precedes(one, other);

            assertThatThrownBy(
                            () ->
                                    new Union(
                                            UUID.randomUUID(),
                                            CLAN,
                                            oneIsSmaller ? other : one,
                                            oneIsSmaller ? one : other,
                                            UnionType.PRIMARY,
                                            UnionStatus.UNKNOWN,
                                            PartialDate.unknown(),
                                            PartialDate.unknown(),
                                            null,
                                            ACTOR,
                                            ACTOR,
                                            NOW,
                                            NOW,
                                            null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("canonical order");
        }

        @Test
        @DisplayName("partnerOf answers from either side and refuses a stranger")
        void partnerOf() {
            PersonId one = PersonId.of(UUID.randomUUID());
            PersonId other = PersonId.of(UUID.randomUUID());
            Union union = union(one, other);

            assertThat(union.partnerOf(one)).isEqualTo(other);
            assertThat(union.partnerOf(other)).isEqualTo(one);
            assertThatThrownBy(() -> union.partnerOf(PersonId.of(UUID.randomUUID())))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("a union cannot end before it began")
        void datesMustBeOrdered() {
            PersonId one = PersonId.of(UUID.randomUUID());
            PersonId other = PersonId.of(UUID.randomUUID());

            assertThatThrownBy(
                            () ->
                                    Union.create(
                                            UUID.randomUUID(),
                                            CLAN,
                                            one,
                                            other,
                                            UnionType.PRIMARY,
                                            UnionStatus.ENDED,
                                            PartialDate.ofYear(1950),
                                            PartialDate.ofYear(1940),
                                            null,
                                            ACTOR,
                                            NOW))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("cannot end before it began");
        }

        @Test
        @DisplayName("I-N3's subject: only a live PRIMARY union counts as a principal marriage")
        void activePrimary() {
            PersonId one = PersonId.of(UUID.randomUUID());
            PersonId other = PersonId.of(UUID.randomUUID());

            assertThat(union(one, other).isActivePrimary()).isTrue();
            assertThat(union(one, other).remove(ACTOR, NOW).isActivePrimary()).isFalse();
            assertThat(
                            union(one, other)
                                    .update(
                                            UnionType.PRIMARY,
                                            UnionStatus.ENDED,
                                            PartialDate.unknown(),
                                            PartialDate.unknown(),
                                            null,
                                            ACTOR,
                                            NOW)
                                    .isActivePrimary())
                    .as("a marriage that ended does not block a later one")
                    .isFalse();
            assertThat(
                            Union.create(
                                            UUID.randomUUID(),
                                            CLAN,
                                            one,
                                            other,
                                            UnionType.SECONDARY,
                                            UnionStatus.ACTIVE,
                                            PartialDate.unknown(),
                                            PartialDate.unknown(),
                                            null,
                                            ACTOR,
                                            NOW)
                                    .isActivePrimary())
                    .as("a vợ lẽ is not a second principal marriage — that is the whole point")
                    .isFalse();
        }

        private Union union(PersonId one, PersonId other) {
            return Union.create(
                    UUID.randomUUID(),
                    CLAN,
                    one,
                    other,
                    UnionType.PRIMARY,
                    UnionStatus.UNKNOWN,
                    PartialDate.unknown(),
                    PartialDate.unknown(),
                    null,
                    ACTOR,
                    NOW);
        }
    }
}
