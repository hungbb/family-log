package app.giaky.linking.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.giaky.common.types.ClanId;
import app.giaky.common.types.PersonId;
import app.giaky.common.types.UserId;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * {@link NodeClaimRequest}: the state machine, and every illegal transition (I-K5).
 *
 * <p>The point of these is not that approve sets {@code APPROVED} — it is that a claim which has
 * already been answered refuses to be answered again, <i>in the domain</i>. A second approval is a
 * double-click away, and the second click must not link a node twice.
 */
class NodeClaimRequestTest {

    private static final Instant NOW = Instant.parse("2026-03-01T09:00:00Z");
    private static final ClanId CLAN = ClanId.of(UUID.randomUUID());
    private static final PersonId PERSON = PersonId.of(UUID.randomUUID());
    private static final UserId REQUESTER = UserId.of(UUID.randomUUID());
    private static final UserId APPROVER = UserId.of(UUID.randomUUID());

    private static NodeClaimRequest pending() {
        return NodeClaimRequest.submit(
                UUID.randomUUID(), CLAN, PERSON, REQUESTER, "Con của ông An", NOW);
    }

    @Nested
    @DisplayName("submitting")
    class Submitting {

        @Test
        @DisplayName("a fresh claim is pending, undecided and unstamped")
        void fresh() {
            NodeClaimRequest claim = pending();

            assertThat(claim.status()).isEqualTo(ClaimStatus.PENDING);
            assertThat(claim.isPending()).isTrue();
            assertThat(claim.decidedBy()).isNull();
            assertThat(claim.decidedAt()).isNull();
            assertThat(claim.decisionReason()).isNull();
            assertThat(claim.createdAt()).isEqualTo(NOW);
        }

        @Test
        @DisplayName("blank evidence is stored as none at all")
        void blankEvidence() {
            NodeClaimRequest claim =
                    NodeClaimRequest.submit(UUID.randomUUID(), CLAN, PERSON, REQUESTER, "   ", NOW);

            // Null and "   " would otherwise render as two different empty states in the inbox.
            assertThat(claim.evidence()).isNull();
        }

        @Test
        @DisplayName("evidence longer than the column is refused rather than truncated")
        void evidenceTooLong() {
            String tooLong = "x".repeat(NodeClaimRequest.EVIDENCE_MAX_LENGTH + 1);

            assertThatThrownBy(
                            () ->
                                    NodeClaimRequest.submit(
                                            UUID.randomUUID(), CLAN, PERSON, REQUESTER, tooLong, NOW))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("evidence");
        }
    }

    @Nested
    @DisplayName("deciding")
    class Deciding {

        @Test
        @DisplayName("approval stamps who and when")
        void approve() {
            NodeClaimRequest decided = pending().approve(APPROVER, NOW.plusSeconds(60));

            assertThat(decided.status()).isEqualTo(ClaimStatus.APPROVED);
            assertThat(decided.decidedBy()).isEqualTo(APPROVER);
            assertThat(decided.decidedAt()).isEqualTo(NOW.plusSeconds(60));
            assertThat(decided.isPending()).isFalse();
        }

        @Test
        @DisplayName("rejection carries the reason the requester will read")
        void reject() {
            NodeClaimRequest decided =
                    pending().reject(APPROVER, "Đây là bác Hai, không phải bạn", NOW);

            assertThat(decided.status()).isEqualTo(ClaimStatus.REJECTED);
            assertThat(decided.decisionReason()).isEqualTo("Đây là bác Hai, không phải bạn");
        }

        @Test
        @DisplayName("cancelling records the requester as the decider, because they decided")
        void cancel() {
            NodeClaimRequest decided = pending().cancel(NOW);

            assertThat(decided.status()).isEqualTo(ClaimStatus.CANCELLED);
            assertThat(decided.decidedBy()).isEqualTo(REQUESTER);
        }

        @Test
        @DisplayName("the created time survives a decision; only updatedAt moves")
        void createdAtIsStable() {
            NodeClaimRequest decided = pending().approve(APPROVER, NOW.plusSeconds(600));

            assertThat(decided.createdAt()).isEqualTo(NOW);
            assertThat(decided.updatedAt()).isEqualTo(NOW.plusSeconds(600));
        }
    }

    @Nested
    @DisplayName("illegal transitions")
    class IllegalTransitions {

        /** Every way out of PENDING, so each terminal state can be tried against every transition. */
        private final List<Function<NodeClaimRequest, NodeClaimRequest>> exits =
                List.of(
                        claim -> claim.approve(APPROVER, NOW),
                        claim -> claim.reject(APPROVER, "no", NOW),
                        claim -> claim.cancel(NOW));

        @Test
        @DisplayName("nothing that has left PENDING can be decided again")
        void oneWayDoor() {
            for (Function<NodeClaimRequest, NodeClaimRequest> exit : exits) {
                NodeClaimRequest decided = exit.apply(pending());

                for (Function<NodeClaimRequest, NodeClaimRequest> second : exits) {
                    assertThatThrownBy(() -> second.apply(decided))
                            .as("a %s claim must refuse a second decision", decided.status())
                            .isInstanceOf(IllegalStateException.class)
                            .hasMessageContaining(decided.status().name());
                }
            }
        }

        @Test
        @DisplayName("a decided claim with no decision time cannot be constructed")
        void decisionTimeIsPaired() {
            assertThatThrownBy(
                            () ->
                                    new NodeClaimRequest(
                                            UUID.randomUUID(),
                                            CLAN,
                                            PERSON,
                                            REQUESTER,
                                            null,
                                            ClaimStatus.APPROVED,
                                            APPROVER,
                                            null,
                                            null,
                                            NOW,
                                            NOW))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("a pending claim with a decision time cannot be constructed either")
        void pendingCarriesNoDecisionTime() {
            assertThatThrownBy(
                            () ->
                                    new NodeClaimRequest(
                                            UUID.randomUUID(),
                                            CLAN,
                                            PERSON,
                                            REQUESTER,
                                            null,
                                            ClaimStatus.PENDING,
                                            null,
                                            NOW,
                                            null,
                                            NOW,
                                            NOW))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
