package app.giaky.clanorg.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.giaky.common.types.BranchId;
import app.giaky.common.types.ClanId;
import app.giaky.common.types.UserId;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** {@link ClanMembership}: the small aggregate that decides who is in. */
class ClanMembershipTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant LATER = NOW.plusSeconds(86_400);
    private static final ClanId CLAN = ClanId.of(new UUID(0L, 1L));
    private static final UserId MEMBER = UserId.of(new UUID(0L, 2L));
    private static final UserId APPROVER = UserId.of(new UUID(0L, 3L));
    private static final BranchId BRANCH = BranchId.of(new UUID(0L, 4L));

    private static ClanMembership active() {
        return ClanMembership.grant(new UUID(0L, 10L), CLAN, MEMBER, BRANCH, APPROVER, NOW);
    }

    @Test
    @DisplayName("a granted membership is active, placed, and has no leaving time")
    void grantShape() {
        ClanMembership membership = active();

        assertThat(membership.isActive()).isTrue();
        assertThat(membership.status()).isEqualTo(MembershipStatus.ACTIVE);
        assertThat(membership.homeBranchId()).isEqualTo(BRANCH);
        assertThat(membership.approvedBy()).isEqualTo(APPROVER);
        assertThat(membership.joinedAt()).isEqualTo(NOW);
        assertThat(membership.leftAt()).isNull();
    }

    @Test
    @DisplayName("leaving and being removed are different facts")
    void leavingAndRemovalAreDistinct() {
        // The audit answer to "why is this person gone?" depends entirely on which of these it was.
        assertThat(active().leave(LATER).status()).isEqualTo(MembershipStatus.LEFT);
        assertThat(active().remove(LATER).status()).isEqualTo(MembershipStatus.REMOVED);
    }

    @Test
    @DisplayName("an ended membership records when it ended")
    void endingStampsTheTime() {
        ClanMembership left = active().leave(LATER);

        assertThat(left.isActive()).isFalse();
        assertThat(left.leftAt()).isEqualTo(LATER);
        // The row survives; a membership is history, not a flag.
        assertThat(left.id()).isEqualTo(active().id());
        assertThat(left.joinedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("ending an ended membership does not move the date")
    void endingIsIdempotent() {
        ClanMembership removed = active().remove(LATER);
        ClanMembership again = removed.remove(LATER.plusSeconds(3600));

        assertThat(again.leftAt()).isEqualTo(LATER);
        assertThat(again.status()).isEqualTo(MembershipStatus.REMOVED);
    }

    @Test
    @DisplayName("a member can be placed in a branch, or in none")
    void placement() {
        BranchId elsewhere = BranchId.of(new UUID(0L, 5L));

        assertThat(active().movedToBranch(elsewhere, LATER).homeBranchId()).isEqualTo(elsewhere);
        assertThat(active().movedToBranch(null, LATER).homeBranchId()).isNull();
    }

    @Test
    @DisplayName("active-with-a-leaving-time is not a state that can be constructed")
    void statusAndLeftAtMustAgree() {
        assertThatThrownBy(
                        () ->
                                new ClanMembership(
                                        new UUID(0L, 11L),
                                        CLAN,
                                        MEMBER,
                                        BRANCH,
                                        MembershipStatus.ACTIVE,
                                        NOW,
                                        LATER,
                                        null,
                                        APPROVER,
                                        NOW,
                                        NOW))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(
                        () ->
                                new ClanMembership(
                                        new UUID(0L, 12L),
                                        CLAN,
                                        MEMBER,
                                        BRANCH,
                                        MembershipStatus.LEFT,
                                        NOW,
                                        null,
                                        null,
                                        APPROVER,
                                        NOW,
                                        NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
