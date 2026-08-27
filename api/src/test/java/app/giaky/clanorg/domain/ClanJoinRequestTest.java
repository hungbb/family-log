package app.giaky.clanorg.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.giaky.common.types.BranchId;
import app.giaky.common.types.ClanId;
import app.giaky.common.types.UserId;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * {@link ClanJoinRequest}: the one-way state machine of I-J4.
 *
 * <p>The illegal transitions get more attention than the legal ones, because the legal ones fail
 * visibly and an illegal one that slips through creates a second membership nobody asked for.
 */
class ClanJoinRequestTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private static final ClanId CLAN = ClanId.of(new UUID(0L, 1L));
    private static final BranchId BRANCH = BranchId.of(new UUID(0L, 2L));
    private static final UserId REQUESTER = UserId.of(new UUID(0L, 3L));
    private static final UserId APPROVER = UserId.of(new UUID(0L, 4L));

    private static ClanJoinRequest pending() {
        return ClanJoinRequest.submit(
                new UUID(0L, 10L), CLAN, BRANCH, REQUESTER, "Cháu nội cụ Ất", NOW);
    }

    @Test
    @DisplayName("a new request is pending and expires ninety days out")
    void submitShape() {
        ClanJoinRequest request = pending();

        assertThat(request.status()).isEqualTo(JoinRequestStatus.PENDING);
        assertThat(request.isPending()).isTrue();
        assertThat(request.decidedAt()).isNull();
        assertThat(request.decidedBy()).isNull();
        assertThat(request.expiresAt()).isEqualTo(NOW.plus(ClanJoinRequest.LIFETIME));
    }

    @Test
    @DisplayName("approval records who and when")
    void approve() {
        ClanJoinRequest approved = pending().approve(APPROVER, NOW.plusSeconds(60));

        assertThat(approved.status()).isEqualTo(JoinRequestStatus.APPROVED);
        assertThat(approved.decidedBy()).isEqualTo(APPROVER);
        assertThat(approved.decidedAt()).isEqualTo(NOW.plusSeconds(60));
    }

    @Test
    @DisplayName("rejection carries the reason the requester will read")
    void reject() {
        ClanJoinRequest rejected = pending().reject(APPROVER, "Không nhận ra tên này", NOW);

        assertThat(rejected.status()).isEqualTo(JoinRequestStatus.REJECTED);
        assertThat(rejected.decisionReason()).isEqualTo("Không nhận ra tên này");
    }

    @Test
    @DisplayName("cancelling records the requester as the decider — they decided")
    void cancel() {
        ClanJoinRequest cancelled = pending().cancel(NOW);

        assertThat(cancelled.status()).isEqualTo(JoinRequestStatus.CANCELLED);
        assertThat(cancelled.decidedBy()).isEqualTo(REQUESTER);
    }

    @Test
    @DisplayName("expiry has no decider, because nobody decided anything")
    void expire() {
        ClanJoinRequest expired = pending().expire(NOW.plus(ClanJoinRequest.LIFETIME));

        assertThat(expired.status()).isEqualTo(JoinRequestStatus.EXPIRED);
        assertThat(expired.decidedBy()).isNull();
        assertThat(expired.decidedAt()).isNotNull();
    }

    @Test
    @DisplayName("expiry is measured against the request's own deadline")
    void hasExpired() {
        ClanJoinRequest request = pending();

        assertThat(request.hasExpired(NOW)).isFalse();
        assertThat(request.hasExpired(request.expiresAt().minusSeconds(1))).isFalse();
        assertThat(request.hasExpired(request.expiresAt())).isTrue();
    }

    /** Every way of leaving PENDING, so the parameterized test below can try each on each. */
    static List<UnaryOperator<ClanJoinRequest>> transitions() {
        return List.of(
                request -> request.approve(APPROVER, NOW),
                request -> request.reject(APPROVER, "no", NOW),
                request -> request.cancel(NOW),
                request -> request.expire(NOW));
    }

    @ParameterizedTest(name = "a decided request refuses transition {index}")
    @MethodSource("transitions")
    @DisplayName("nothing that has left PENDING can be decided again (I-J4)")
    void decidedRequestsAreFinal(UnaryOperator<ClanJoinRequest> transition) {
        // The case this really guards: a double-clicked approve button must not create a second
        // membership. Every terminal state refuses every transition, not just the same one.
        for (UnaryOperator<ClanJoinRequest> first : transitions()) {
            ClanJoinRequest decided = first.apply(pending());

            assertThatThrownBy(() -> transition.apply(decided))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining(decided.status().name());
        }
    }

    @ParameterizedTest
    @EnumSource(JoinRequestStatus.class)
    @DisplayName("exactly one status is decidable")
    void onlyPendingIsDecidable(JoinRequestStatus status) {
        assertThat(status.isDecidable()).isEqualTo(status == JoinRequestStatus.PENDING);
    }

    @Test
    @DisplayName("a pending request with a decision time is not a state that can be constructed")
    void decisionTimeAndStatusMustAgree() {
        assertThatThrownBy(
                        () ->
                                new ClanJoinRequest(
                                        new UUID(0L, 11L),
                                        CLAN,
                                        BRANCH,
                                        REQUESTER,
                                        null,
                                        JoinRequestStatus.PENDING,
                                        null,
                                        NOW,
                                        null,
                                        NOW,
                                        NOW,
                                        NOW.plus(ClanJoinRequest.LIFETIME)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("a blank message is stored as absent, not as an empty string")
    void blankMessageBecomesNull() {
        ClanJoinRequest request =
                ClanJoinRequest.submit(new UUID(0L, 12L), CLAN, null, REQUESTER, "   ", NOW);

        assertThat(request.message()).isNull();
        assertThat(request.targetBranchId()).isNull();
    }
}
