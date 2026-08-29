package app.giaky.notification.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import app.giaky.authorization.application.PermissionQueryService;
import app.giaky.authorization.domain.Permission;
import app.giaky.authorization.domain.Target;
import app.giaky.common.types.BranchId;
import app.giaky.common.types.ClanId;
import app.giaky.common.types.PersonId;
import app.giaky.common.types.UserId;
import app.giaky.identity.application.UserDirectory;
import app.giaky.linking.application.event.ClaimDecided;
import app.giaky.linking.application.event.ClaimSubmitted;
import app.giaky.linking.application.event.PersonLinked;
import app.giaky.linking.application.event.PersonUnlinked;
import app.giaky.notification.domain.NotificationType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * {@link LinkingNotificationListener}: who hears about what (B6.9, FR-N2).
 *
 * <p>The interesting assertions are the negative ones. A person who withdraws their own claim, or
 * unlinks themselves, must not be notified about it — the confirmation is the screen in front of
 * them, and a notification saying "you did this" is the kind of noise that makes people stop reading
 * the list at all.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LinkingNotificationListenerTest {

    private static final Instant NOW = Instant.parse("2026-03-01T09:00:00Z");
    private static final ClanId CLAN = ClanId.of(UUID.randomUUID());
    private static final PersonId PERSON = PersonId.of(UUID.randomUUID());
    private static final BranchId BRANCH = BranchId.of(UUID.randomUUID());
    private static final UUID CLAIM = UUID.randomUUID();

    private final UserId requester = UserId.of(UUID.randomUUID());
    private final UserId approver = UserId.of(UUID.randomUUID());

    @Mock private NotificationService notifications;
    @Mock private PermissionQueryService permissions;
    @Mock private UserDirectory users;

    private LinkingNotificationListener listener;

    @BeforeEach
    void setUp() {
        listener = new LinkingNotificationListener(notifications, permissions, users);
        when(users.profile(any())).thenReturn(Optional.empty());
    }

    @Test
    @DisplayName("a claim goes to the approvers who cover the node, and to nobody else")
    void routesToApproversInScope() {
        when(permissions.holdersCovering(
                        eq(CLAN),
                        eq(Permission.NODE_CLAIM_APPROVE),
                        any(Target.PersonTarget.class)))
                .thenReturn(Set.of(approver));

        listener.onClaimSubmitted(
                new ClaimSubmitted(CLAN, CLAIM, PERSON, BRANCH, "Nguyễn Văn An", requester, NOW));

        ArgumentCaptor<List<UserId>> recipients = ArgumentCaptor.captor();
        verify(notifications)
                .raiseAll(
                        recipients.capture(),
                        eq(CLAN),
                        eq(NotificationType.CLAIM_SUBMITTED),
                        any());
        org.assertj.core.api.Assertions.assertThat(recipients.getValue())
                .containsExactly(approver);
    }

    @Test
    @DisplayName("a claimant who is also an approver is not told about their own claim")
    void doesNotNotifyTheRequester() {
        when(permissions.holdersCovering(any(), any(), any())).thenReturn(Set.of(requester));

        listener.onClaimSubmitted(
                new ClaimSubmitted(CLAN, CLAIM, PERSON, BRANCH, "Nguyễn Văn An", requester, NOW));

        verify(notifications, never()).raiseAll(anyList(), any(), any(), any());
    }

    @Test
    @DisplayName("a decision goes back to the requester")
    void decisionReachesTheRequester() {
        listener.onClaimDecided(
                new ClaimDecided(
                        CLAN,
                        CLAIM,
                        PERSON,
                        "Nguyễn Văn An",
                        requester,
                        ClaimDecided.Outcome.APPROVED,
                        null,
                        approver,
                        NOW));

        verify(notifications)
                .raise(eq(requester), eq(CLAN), eq(NotificationType.CLAIM_DECIDED), any());
    }

    @Test
    @DisplayName("withdrawing your own claim notifies nobody")
    void cancellationIsSilent() {
        listener.onClaimDecided(
                new ClaimDecided(
                        CLAN,
                        CLAIM,
                        PERSON,
                        "Nguyễn Văn An",
                        requester,
                        ClaimDecided.Outcome.CANCELLED,
                        null,
                        requester,
                        NOW));

        verifyNoInteractions(notifications);
    }

    @Test
    @DisplayName("a direct assignment tells the person it was done to")
    void directAssignmentIsNews() {
        listener.onPersonLinked(
                new PersonLinked(CLAN, PERSON, "Nguyễn Văn An", requester, approver, false, NOW));

        verify(notifications)
                .raise(eq(requester), eq(CLAN), eq(NotificationType.NODE_LINKED), any());
    }

    @Test
    @DisplayName("a link that came from a claim does not, because the decision already said so")
    void noDoubleNotification() {
        listener.onPersonLinked(
                new PersonLinked(CLAN, PERSON, "Nguyễn Văn An", requester, approver, true, NOW));

        verifyNoInteractions(notifications);
    }

    @Test
    @DisplayName("being unlinked by somebody else is worth knowing")
    void unlinkedByAnother() {
        listener.onPersonUnlinked(
                new PersonUnlinked(CLAN, PERSON, "Nguyễn Văn An", requester, approver, false, NOW));

        verify(notifications)
                .raise(eq(requester), eq(CLAN), eq(NotificationType.NODE_UNLINKED), any());
    }

    @Test
    @DisplayName("unlinking yourself is not")
    void selfUnlinkIsSilent() {
        listener.onPersonUnlinked(
                new PersonUnlinked(CLAN, PERSON, "Nguyễn Văn An", requester, requester, true, NOW));

        verifyNoInteractions(notifications);
    }
}
