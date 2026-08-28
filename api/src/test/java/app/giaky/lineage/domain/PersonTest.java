package app.giaky.lineage.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.giaky.common.types.BranchId;
import app.giaky.common.types.ClanId;
import app.giaky.common.types.PartialDate;
import app.giaky.common.types.PersonId;
import app.giaky.common.types.UserId;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The {@link Person} invariants that can be checked on one aggregate: I-P1, I-P2, I-P3, I-P7. */
class PersonTest {

    private static final ClanId CLAN = ClanId.of(UUID.randomUUID());
    private static final BranchId BRANCH = BranchId.of(UUID.randomUUID());
    private static final UserId ACTOR = UserId.of(UUID.randomUUID());
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    @DisplayName("I-P1: a blank name is not a person")
    void nameMustNotBeBlank() {
        assertThatThrownBy(() -> PersonName.of("   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not be blank");
    }

    @Test
    @DisplayName("I-P1: the name is trimmed, and the parts are optional")
    void nameIsTrimmed() {
        PersonName name = new PersonName("  Nguyễn Văn An  ", null, null, "  ", null);

        assertThat(name.fullName()).isEqualTo("Nguyễn Văn An");
        assertThat(name.alias()).as("a blank part is no part at all").isNull();
    }

    @Test
    @DisplayName("I-P2: a birth certainly after a death is refused")
    void birthCannotFollowDeath() {
        assertThatThrownBy(
                        () ->
                                person()
                                        .birth(PartialDate.ofYear(1968))
                                        .death(PartialDate.ofYear(1901))
                                        .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("I-P2");
    }

    @Test
    @DisplayName("I-P2: dates that only might be the wrong way round are accepted")
    void indeterminateDatesAreAccepted() {
        // Born and died in the same year is a real record, and the dates do not settle the order.
        // Refusing it would mean refusing to transcribe an infant death.
        assertThatCode(
                        () ->
                                person()
                                        .birth(PartialDate.ofYear(1901))
                                        .death(PartialDate.ofYear(1901))
                                        .build())
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("I-P3: somebody alive cannot have a date of death")
    void livingPeopleHaveNoDeathDate() {
        assertThatThrownBy(
                        () ->
                                person()
                                        .lifeStatus(LifeStatus.ALIVE)
                                        .death(PartialDate.ofYear(1968))
                                        .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("I-P3");
    }

    @Test
    @DisplayName("a new person has no generation unless they are the founding ancestor")
    void generationStartsUnknown() {
        assertThat(person().build().generation()).isNull();
        assertThat(person().founding().build().generation()).isEqualTo(Person.FIRST_GENERATION);
    }

    @Test
    @DisplayName("generation counts from one; zero would be a lie the UI renders as a fact")
    void generationCountsFromOne() {
        Person person = person().build();

        assertThatThrownBy(() -> person.withGeneration(0, NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("counts from 1");
        assertThat(person.withGeneration(null, NOW).generation()).isNull();
    }

    @Test
    @DisplayName("withGeneration returns the same object when nothing changed")
    void generationIsIdempotent() {
        Person person = person().build().withGeneration(4, NOW);

        assertThat(person.withGeneration(4, NOW)).isSameAs(person);
    }

    @Test
    @DisplayName("I-P7: soft delete is reversible and leaves everything else alone")
    void softDeleteAndRestore() {
        Person person = person().build();
        Person deleted = person.softDelete(ACTOR, NOW);

        assertThat(deleted.isDeleted()).isTrue();
        assertThat(deleted.deletedAt()).isEqualTo(NOW);
        assertThat(deleted.branchId()).isEqualTo(person.branchId());
        assertThat(deleted.softDelete(ACTOR, NOW.plusSeconds(60)))
                .as("deleting twice does not move the timestamp")
                .isSameAs(deleted);

        Person restored = deleted.restore(ACTOR, NOW.plusSeconds(60));
        assertThat(restored.isDeleted()).isFalse();
        assertThat(restored.fullName()).isEqualTo(person.fullName());
    }

    @Test
    @DisplayName("an unplaced person has no branch, and no branch grant can reach them")
    void unplacedHasNoBranch() {
        Person person = person().branch(null).build();

        assertThat(person.isPlaced()).isFalse();
        assertThat(person.branchId()).isNull();
    }

    @Test
    @DisplayName("moving to a branch changes only the placement and the audit columns")
    void movedTo() {
        BranchId destination = BranchId.of(UUID.randomUUID());
        Person moved = person().build().movedTo(destination, ACTOR, NOW.plusSeconds(60));

        assertThat(moved.branchId()).isEqualTo(destination);
        assertThat(moved.updatedBy()).isEqualTo(ACTOR);
        assertThat(moved.createdAt()).isEqualTo(NOW);
    }

    // --- a small builder, so each test says only what it is about --------------------------------

    private static Builder person() {
        return new Builder();
    }

    private static final class Builder {

        private LifeStatus lifeStatus = LifeStatus.UNKNOWN;
        private PartialDate birth = PartialDate.unknown();
        private PartialDate death = PartialDate.unknown();
        private BranchId branchId = BRANCH;
        private boolean founding;

        Builder lifeStatus(LifeStatus value) {
            this.lifeStatus = value;
            return this;
        }

        Builder birth(PartialDate value) {
            this.birth = value;
            return this;
        }

        Builder death(PartialDate value) {
            this.death = value;
            return this;
        }

        Builder branch(BranchId value) {
            this.branchId = value;
            return this;
        }

        Builder founding() {
            this.founding = true;
            return this;
        }

        Person build() {
            return Person.create(
                    PersonId.of(UUID.randomUUID()),
                    CLAN,
                    branchId,
                    PersonName.of("Nguyễn Văn An"),
                    Gender.MALE,
                    lifeStatus,
                    birth,
                    death,
                    null,
                    null,
                    null,
                    null,
                    founding,
                    ACTOR,
                    NOW);
        }
    }
}
