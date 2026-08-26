package app.giaky.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.giaky.common.types.UserId;
import app.giaky.identity.domain.RefreshToken.RevocationReason;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** The state machines of {@link RefreshToken} and {@link VerificationToken}. */
class TokenLifecycleTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private static final UserId USER = UserId.of(UUID.randomUUID());

    @Nested
    @DisplayName("refresh token")
    class Refresh {

        private RefreshToken opened() {
            return RefreshToken.open(
                    UUID.randomUUID(),
                    USER,
                    "hash-1",
                    NOW,
                    NOW.plus(Duration.ofDays(30)),
                    "Firefox",
                    "203.0.113.7");
        }

        @Test
        @DisplayName("a new session starts its own chain, named after its first token")
        void openStartsAChain() {
            RefreshToken token = opened();

            assertThat(token.chainId()).isEqualTo(token.id());
            assertThat(token.isRevoked()).isFalse();
            assertThat(token.isUsableAt(NOW)).isTrue();
        }

        @Test
        @DisplayName("a successor stays in the same chain and inherits the client details")
        void successorStaysInTheChain() {
            RefreshToken first = opened();
            RefreshToken second =
                    first.successor(
                            UUID.randomUUID(),
                            "hash-2",
                            NOW.plusSeconds(60),
                            NOW.plus(Duration.ofDays(30)));

            assertThat(second.chainId()).isEqualTo(first.chainId());
            assertThat(second.id()).isNotEqualTo(first.id());
            assertThat(second.userAgent()).isEqualTo("Firefox");
            assertThat(second.ipAddress()).isEqualTo("203.0.113.7");
            assertThat(second.isRevoked()).isFalse();
        }

        @Test
        @DisplayName("expiry makes it unusable without revoking it")
        void expiryEndsUsability() {
            RefreshToken token = opened();
            Instant afterExpiry = NOW.plus(Duration.ofDays(30));

            assertThat(token.isExpiredAt(afterExpiry)).isTrue();
            assertThat(token.isUsableAt(afterExpiry)).isFalse();
            assertThat(token.isRevoked()).isFalse();
        }

        @Test
        @DisplayName("revoking records when and why")
        void revocationIsRecorded() {
            RefreshToken revoked = opened().revoke(RevocationReason.LOGOUT, NOW.plusSeconds(5));

            assertThat(revoked.isRevoked()).isTrue();
            assertThat(revoked.revokedAt()).isEqualTo(NOW.plusSeconds(5));
            assertThat(revoked.revokedReason()).isEqualTo(RevocationReason.LOGOUT);
            assertThat(revoked.isUsableAt(NOW.plusSeconds(6))).isFalse();
        }

        @Test
        @DisplayName("re-revoking keeps the original reason, so ROTATED is not overwritten by LOGOUT_ALL")
        void reRevocationKeepsTheFirstReason() {
            RefreshToken rotated = opened().revoke(RevocationReason.ROTATED, NOW.plusSeconds(5));

            RefreshToken again = rotated.revoke(RevocationReason.LOGOUT_ALL, NOW.plusSeconds(9));

            assertThat(again).isSameAs(rotated);
            assertThat(again.revokedReason()).isEqualTo(RevocationReason.ROTATED);
        }

        @Test
        @DisplayName("a half-set revocation cannot be constructed")
        void revocationFieldsAreSetTogether() {
            assertThatThrownBy(
                            () ->
                                    new RefreshToken(
                                            UUID.randomUUID(),
                                            USER,
                                            UUID.randomUUID(),
                                            "hash",
                                            NOW,
                                            NOW.plusSeconds(60),
                                            NOW,
                                            null,
                                            null,
                                            null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("expiry must be after issue")
        void expiryMustFollowIssue() {
            assertThatThrownBy(
                            () ->
                                    RefreshToken.open(
                                            UUID.randomUUID(), USER, "hash", NOW, NOW, null, null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("verification token")
    class Verification {

        private VerificationToken issued(TokenPurpose purpose, Duration lifetime) {
            return VerificationToken.issue(
                    UUID.randomUUID(), USER, purpose, "hash-1", NOW, NOW.plus(lifetime));
        }

        @Test
        @DisplayName("is usable until it expires")
        void usableUntilExpiry() {
            VerificationToken token = issued(TokenPurpose.EMAIL_VERIFY, Duration.ofHours(24));

            assertThat(token.isUsableAt(NOW)).isTrue();
            assertThat(token.isUsableAt(NOW.plus(Duration.ofHours(23)))).isTrue();
            assertThat(token.isUsableAt(NOW.plus(Duration.ofHours(24)))).isFalse();
        }

        @Test
        @DisplayName("consuming it spends it for good")
        void consumingSpendsIt() {
            VerificationToken consumed =
                    issued(TokenPurpose.PASSWORD_RESET, Duration.ofHours(1))
                            .consume(NOW.plusSeconds(30));

            assertThat(consumed.isConsumed()).isTrue();
            assertThat(consumed.consumedAt()).isEqualTo(NOW.plusSeconds(30));
            assertThat(consumed.isUsableAt(NOW.plusSeconds(31))).isFalse();
        }

        @Test
        @DisplayName("consuming twice keeps the first timestamp")
        void secondConsumeIsANoOp() {
            VerificationToken once =
                    issued(TokenPurpose.EMAIL_VERIFY, Duration.ofHours(24)).consume(NOW.plusSeconds(1));

            assertThat(once.consume(NOW.plusSeconds(2))).isSameAs(once);
        }

        @Test
        @DisplayName("each purpose carries its own lifetime (FR-A6)")
        void purposesHaveDifferentLifetimes() {
            Duration verify = Duration.ofHours(24);
            Duration reset = Duration.ofHours(1);

            assertThat(TokenPurpose.EMAIL_VERIFY.lifetime(verify, reset)).isEqualTo(verify);
            assertThat(TokenPurpose.PASSWORD_RESET.lifetime(verify, reset)).isEqualTo(reset);
        }
    }
}
