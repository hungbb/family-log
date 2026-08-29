package app.giaky.linking.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.giaky.common.error.PermissionDeniedException;
import app.giaky.common.types.BranchId;
import app.giaky.common.types.ClanId;
import app.giaky.common.types.PersonId;
import app.giaky.common.types.UserId;
import app.giaky.identity.application.event.ProfileUpdated;
import app.giaky.lineage.application.UpdatePersonCommand;
import app.giaky.lineage.domain.Gender;
import app.giaky.linking.support.LinkingTestFixtures;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * {@link SelfProfileService}: the self-service exception of doc 04 §6 — <b>both directions</b>.
 *
 * <p>Phase 6's exit criteria ask for the whitelist to be tested for allow <i>and</i> deny, because a
 * whitelist tested only in the allowing direction is a whitelist nobody has checked the edges of.
 * The deny half lives here rather than in a Lineage test on purpose: it is the same person, the same
 * node, and the same request — the only difference is which field they reached for.
 */
class SelfProfileServiceTest {

    private LinkingTestFixtures fixtures;

    private UserId founder;
    private ClanId clanId;
    private BranchId chiHai;
    private PersonId son;
    private UserId member;

    @BeforeEach
    void setUp() {
        fixtures = new LinkingTestFixtures();
        founder = fixtures.lineage.clanOrg.newUser("Nguyễn Văn Tổ");
        clanId = fixtures.lineage.clanOrg.newClan(founder, "Nguyễn Văn");
        BranchId rootBranch = fixtures.lineage.rootBranch(clanId);
        chiHai = fixtures.lineage.clanOrg.newBranch(founder, clanId, rootBranch, "Chi Hai");

        PersonId ancestor = fixtures.lineage.founder(founder, clanId, rootBranch, "Nguyễn Văn Ất");
        son = fixtures.lineage.childOf(founder, clanId, ancestor, "Nguyễn Văn An", Gender.MALE);
        fixtures.lineage.personService.moveToBranch(founder, clanId, son, chiHai);

        member = fixtures.newMember(clanId, chiHai, "Nguyễn Văn An");
        fixtures.personLinkService.assign(founder, clanId, son, member);
    }

    @Nested
    @DisplayName("what the whitelist allows")
    class Allowed {

        @Test
        @DisplayName("the linked member edits their own notes without holding TREE_EDIT")
        void editsOwnNotes() {
            fixtures.selfProfileService.update(
                    member,
                    clanId,
                    son,
                    new SelfProfileCommand(null, "<p>Sống ở Nam Định</p>", false, false));

            assertThat(fixtures.lineage.personService.detail(member, clanId, son).notesHtml())
                    .contains("Nam Định");
        }

        @Test
        @DisplayName("notes are sanitized on the way in, like every other note write (B4.13)")
        void sanitizes() {
            fixtures.selfProfileService.update(
                    member,
                    clanId,
                    son,
                    new SelfProfileCommand(null, "<p>ổn</p><script>alert(1)</script>", false, false));

            assertThat(fixtures.lineage.personService.detail(member, clanId, son).notesHtml())
                    .doesNotContain("script");
        }

        @Test
        @DisplayName("a photo is one of the two fields")
        void editsOwnAvatar() {
            UUID media = UUID.randomUUID();

            fixtures.selfProfileService.update(
                    member, clanId, son, new SelfProfileCommand(media, null, false, false));

            assertThat(fixtures.lineage.personService.detail(member, clanId, son).avatarMediaId())
                    .isEqualTo(media);
        }
    }

    @Nested
    @DisplayName("what it refuses")
    class Refused {

        @Test
        @DisplayName("the same member cannot rename their own node — that is still TREE_EDIT")
        void cannotEditFullName() {
            long version = fixtures.lineage.personService.detail(member, clanId, son).version();

            assertThatThrownBy(
                            () ->
                                    fixtures.lineage.personService.patch(
                                            member,
                                            clanId,
                                            son,
                                            new UpdatePersonCommand(
                                                    "Nguyễn Văn Khác",
                                                    null,
                                                    null,
                                                    null,
                                                    null,
                                                    null,
                                                    null,
                                                    null,
                                                    null,
                                                    null,
                                                    null,
                                                    null,
                                                    version)))
                    .isInstanceOf(PermissionDeniedException.class);
        }

        @Test
        @DisplayName("nobody may use the self-service path on a node that is not theirs")
        void cannotEditSomebodyElsesNode() {
            UserId other = fixtures.newMember(clanId, chiHai, "Người khác");

            assertThatThrownBy(
                            () ->
                                    fixtures.selfProfileService.update(
                                            other,
                                            clanId,
                                            son,
                                            new SelfProfileCommand(null, "<p>hi</p>", false, false)))
                    .isInstanceOf(PermissionDeniedException.class);
        }

        @Test
        @DisplayName("not even somebody who holds TREE_EDIT — this door is identity, not permission")
        void notEvenAnEditor() {
            assertThatThrownBy(
                            () ->
                                    fixtures.selfProfileService.update(
                                            founder,
                                            clanId,
                                            son,
                                            new SelfProfileCommand(null, "<p>hi</p>", false, false)))
                    .isInstanceOf(PermissionDeniedException.class);
        }
    }

    @Nested
    @DisplayName("opt-in propagation (FR-N6)")
    class Propagation {

        @Test
        @DisplayName("both flags are off until somebody turns them on")
        void defaultsOff() {
            assertThat(fixtures.selfProfileService.flags(member, clanId, son).anyEnabled()).isFalse();
        }

        @Test
        @DisplayName("a profile edit changes nothing while the flags are off")
        void nothingPropagatesByDefault() {
            fixtures.propagationListener.onProfileUpdated(
                    new ProfileUpdated(
                            member,
                            "Tên mới",
                            UUID.randomUUID(),
                            fixtures.lineage.clanOrg.clock.instant()));

            assertThat(fixtures.lineage.personService.detail(member, clanId, son).alias()).isNull();
        }

        @Test
        @DisplayName("turning a flag on applies it at once, without a second profile save")
        void appliesImmediately() {
            // Somebody who has just ticked "use my name" expects to see it, not to have to go and
            // re-save their profile to make it happen.
            fixtures.selfProfileService.update(
                    member, clanId, son, new SelfProfileCommand(null, null, false, true));

            assertThat(fixtures.lineage.personService.detail(member, clanId, son).alias())
                    .isEqualTo("Nguyễn Văn An");
        }

        @Test
        @DisplayName("the display name lands on the alias, never on the name the book records")
        void displayNameGoesToAlias() {
            fixtures.selfProfileService.update(
                    member, clanId, son, new SelfProfileCommand(null, null, false, true));

            fixtures.propagationListener.onProfileUpdated(
                    new ProfileUpdated(
                            member, "Anh Hai", null, fixtures.lineage.clanOrg.clock.instant()));

            assertThat(fixtures.lineage.personService.detail(member, clanId, son))
                    .satisfies(
                            person -> {
                                assertThat(person.alias()).isEqualTo("Anh Hai");
                                assertThat(person.fullName()).isEqualTo("Nguyễn Văn An");
                            });
        }

        @Test
        @DisplayName("only the fields whose flag is on move")
        void perFieldOptIn() {
            fixtures.selfProfileService.update(
                    member, clanId, son, new SelfProfileCommand(null, null, false, true));

            UUID media = UUID.randomUUID();
            fixtures.propagationListener.onProfileUpdated(
                    new ProfileUpdated(
                            member, "Anh Hai", media, fixtures.lineage.clanOrg.clock.instant()));

            assertThat(fixtures.lineage.personService.detail(member, clanId, son))
                    .satisfies(
                            person -> {
                                assertThat(person.alias()).isEqualTo("Anh Hai");
                                assertThat(person.avatarMediaId()).isNull();
                            });
        }
    }
}
