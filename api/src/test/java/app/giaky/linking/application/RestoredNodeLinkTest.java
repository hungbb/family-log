package app.giaky.linking.application;

import static org.assertj.core.api.Assertions.assertThat;

import app.giaky.common.audit.AuditAction;
import app.giaky.common.types.BranchId;
import app.giaky.common.types.ClanId;
import app.giaky.common.types.PersonId;
import app.giaky.common.types.UserId;
import app.giaky.lineage.domain.Gender;
import app.giaky.linking.support.LinkingTestFixtures;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * What happens to a link when its node is deleted and then restored (FR-P10 meeting I-P4 and I-P5).
 *
 * <p>A deleted node keeps its link, and the index behind I-P4 does not see deleted rows — so the
 * account can be given another node while the first is gone. Restoring the first used to be a
 * unique-constraint violation answered as a {@code 500}; these pin what it does instead. The
 * constraint itself only exists in PostgreSQL, so the same sequence is also driven over HTTP in
 * {@code NodeClaimingIT}.
 */
class RestoredNodeLinkTest {

    private LinkingTestFixtures fixtures;

    private UserId founder;
    private ClanId clanId;
    private BranchId rootBranch;

    private PersonId ancestor;
    private PersonId son;
    private PersonId daughter;

    private UserId member;

    @BeforeEach
    void setUp() {
        fixtures = new LinkingTestFixtures();
        founder = fixtures.lineage.clanOrg.newUser("Nguyễn Văn Tổ");
        clanId = fixtures.lineage.clanOrg.newClan(founder, "Nguyễn Văn");
        rootBranch = fixtures.lineage.rootBranch(clanId);

        ancestor = fixtures.lineage.founder(founder, clanId, rootBranch, "Nguyễn Văn Ất");
        son = fixtures.lineage.childOf(founder, clanId, ancestor, "Nguyễn Văn An", Gender.MALE);
        daughter =
                fixtures.lineage.childOf(founder, clanId, ancestor, "Nguyễn Thị Bé", Gender.FEMALE);

        member = fixtures.newMember(clanId, rootBranch, "Nguyễn Văn An");
        fixtures.personLinkService.assign(founder, clanId, son, member);
    }

    private UUID linkedUserOf(PersonId personId) {
        return fixtures.personLinkService.linkOf(founder, clanId, personId).linkedUserId();
    }

    @Test
    @DisplayName("a mistaken delete, undone, gives the member their node back")
    void restoreKeepsALinkThatStillHolds() {
        fixtures.lineage.personService.softDelete(founder, clanId, son);
        fixtures.lineage.clanOrg.audit.clear();

        fixtures.lineage.personService.restore(founder, clanId, son);

        assertThat(linkedUserOf(son)).isEqualTo(member.value());
        assertThat(fixtures.personLinkService.myNode(member, clanId)).isPresent();
        assertThat(fixtures.lineage.clanOrg.audit.recorded(AuditAction.PERSON_UNLINKED))
                .isFalse();
    }

    @Test
    @DisplayName("…but not when they were given another node in the meantime (I-P4)")
    void restoreDropsALinkTheAccountHasMovedOnFrom() {
        fixtures.lineage.personService.softDelete(founder, clanId, son);
        fixtures.personLinkService.assign(founder, clanId, daughter, member);
        fixtures.lineage.clanOrg.audit.clear();

        fixtures.lineage.personService.restore(founder, clanId, son);

        assertThat(linkedUserOf(son)).isNull();
        // The node they hold now is the one they keep.
        assertThat(linkedUserOf(daughter)).isEqualTo(member.value());
        assertThat(fixtures.personLinkService.myNode(member, clanId))
                .get()
                .extracting(LinkedNodeView::personId)
                .isEqualTo(daughter.value());
        assertThat(fixtures.lineage.clanOrg.audit.actions())
                .containsExactly(AuditAction.PERSON_RESTORED, AuditAction.PERSON_UNLINKED);
    }

    @Test
    @DisplayName("…and not when their membership ended in the meantime (I-P5)")
    void restoreDropsTheLinkOfSomebodyWhoLeft() {
        fixtures.lineage.personService.softDelete(founder, clanId, son);
        fixtures.lineage.clanOrg.membershipService.remove(founder, clanId, member);

        fixtures.lineage.personService.restore(founder, clanId, son);

        assertThat(linkedUserOf(son)).isNull();
    }

    @Test
    @DisplayName("a node restored unlinked can be claimed by somebody else straight away")
    void restoredUnlinkedNodeIsBackInThePool() {
        fixtures.lineage.personService.softDelete(founder, clanId, son);
        fixtures.personLinkService.assign(founder, clanId, daughter, member);
        fixtures.lineage.personService.restore(founder, clanId, son);
        UserId cousin = fixtures.newMember(clanId, rootBranch, "Nguyễn Văn Em");

        fixtures.personLinkService.assign(founder, clanId, son, cousin);

        assertThat(linkedUserOf(son)).isEqualTo(cousin.value());
    }

    @Test
    @DisplayName("the former occupant's propagation choices do not come back with the node")
    void restoreUnlinkedForgetsPropagation() {
        fixtures.selfProfileService.update(
                member, clanId, son, new SelfProfileCommand(null, null, true, true));
        fixtures.lineage.personService.softDelete(founder, clanId, son);
        fixtures.personLinkService.assign(founder, clanId, daughter, member);

        fixtures.lineage.personService.restore(founder, clanId, son);

        assertThat(fixtures.propagation.find(clanId, son).anyEnabled()).isFalse();
    }

    @Test
    @DisplayName("…and are kept when the link is")
    void restoreLinkedKeepsPropagation() {
        fixtures.selfProfileService.update(
                member, clanId, son, new SelfProfileCommand(null, null, true, true));
        fixtures.lineage.personService.softDelete(founder, clanId, son);

        fixtures.lineage.personService.restore(founder, clanId, son);

        assertThat(fixtures.propagation.find(clanId, son).anyEnabled()).isTrue();
    }
}
