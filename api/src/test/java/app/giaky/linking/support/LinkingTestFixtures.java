package app.giaky.linking.support;

import app.giaky.clanorg.application.event.MembershipGranted;
import app.giaky.clanorg.domain.ClanMembership;
import app.giaky.common.types.BranchId;
import app.giaky.common.types.ClanId;
import app.giaky.common.types.UserId;
import app.giaky.lineage.application.PersonLinkGateway;
import app.giaky.lineage.application.event.PersonRestored;
import app.giaky.lineage.support.LineageTestFixtures;
import app.giaky.linking.application.ClaimService;
import app.giaky.linking.application.PersonLinkService;
import app.giaky.linking.application.ProfilePropagationListener;
import app.giaky.linking.application.RestoredNodeListener;
import app.giaky.linking.application.SelfProfileService;
import app.giaky.linking.domain.ClanMemberPort;
import app.giaky.linking.domain.PersonLinkPort;
import app.giaky.linking.domain.PersonUserLinker;
import app.giaky.linking.infrastructure.ClanOrgClanMemberAdapter;
import app.giaky.linking.infrastructure.LineagePersonLinkAdapter;
import java.time.Instant;

/**
 * The whole Linking application graph, wired in memory, on top of {@link LineageTestFixtures}.
 *
 * <p>Every service here is the real one, the permission resolver and {@link PersonUserLinker}
 * included. What is replaced is the two repositories this context owns; everything else is the production object graph, wired by hand rather than by Spring.
 *
 * <p>The adapters are real too, so a test exercising a claim goes through the same
 * {@code PersonLinkPort} → gateway → repository path production does. That matters more here than
 * elsewhere: the seam is where the "one writer" guarantee lives.
 *
 * <p>Notifications are deliberately absent. {@code NotificationService} speaks to a Spring Data
 * repository rather than to a port — doc 06 §2.1 exempts that table from the domain split — and a
 * hand-written double for it would be forty methods of nothing. The listener that routes claims to
 * approvers is unit-tested against mocks in {@code LinkingNotificationListenerTest}, and the wiring
 * end to end is covered by {@code NodeClaimingIT}.
 */
public final class LinkingTestFixtures {

    /** Persons, clans, branches, memberships, grants — everything Linking sits on top of. */
    public final LineageTestFixtures lineage = new LineageTestFixtures();

    public final InMemoryNodeClaimRequestRepository claims =
            new InMemoryNodeClaimRequestRepository();
    public final InMemoryProfilePropagationRepository propagation =
            new InMemoryProfilePropagationRepository();

    public final PersonLinkGateway gateway;
    public final PersonLinkPort linkPort;
    public final ClanMemberPort memberPort;
    public final PersonUserLinker linker;

    public final ClaimService claimService;
    public final PersonLinkService personLinkService;
    public final SelfProfileService selfProfileService;
    public final RestoredNodeListener restoredNodes;
    public final ProfilePropagationListener propagationListener;

    public LinkingTestFixtures() {
        gateway =
                new PersonLinkGateway(
                        lineage.persons,
                        lineage.personService,
                        lineage.graph,
                        lineage.treeVersions,
                        lineage.sanitizer,
                        lineage.clanOrg.clock);
        linkPort = new LineagePersonLinkAdapter(gateway);
        memberPort = new ClanOrgClanMemberAdapter(lineage.clanOrg.clanDirectory);
        linker = new PersonUserLinker(linkPort, memberPort);

        claimService =
                new ClaimService(
                        claims,
                        linker,
                        gateway,
                        lineage.clanOrg.resolver,
                        lineage.clanOrg.clanDirectory,
                        lineage.clanOrg.directory,
                        lineage.clanOrg.audit,
                        lineage.clanOrg.events,
                        lineage.clanOrg.ids,
                        lineage.clanOrg.clock);

        personLinkService =
                new PersonLinkService(
                        linker,
                        gateway,
                        propagation,
                        lineage.clanOrg.resolver,
                        lineage.clanOrg.directory,
                        lineage.clanOrg.audit,
                        lineage.clanOrg.events,
                        lineage.clanOrg.clock);

        // Spring does this by annotation; a restored node that comes back unlinked must not keep
        // the propagation choices of whoever used to be on it.
        restoredNodes = new RestoredNodeListener(gateway, propagation);
        lineage.clanOrg.events.subscribe(PersonRestored.class, restoredNodes::onPersonRestored);

        selfProfileService =
                new SelfProfileService(
                        linker,
                        gateway,
                        propagation,
                        lineage.clanOrg.resolver,
                        lineage.clanOrg.directory,
                        lineage.clanOrg.audit);

        propagationListener = new ProfilePropagationListener(gateway, propagation);
    }

    /**
     * An account with an {@code ACTIVE} membership and the {@code CLAN_VIEW} that comes with it.
     *
     * <p>Goes through {@code MembershipGranted} rather than writing a grant directly, so a member
     * made here holds exactly what a member made by an approved join request holds.
     */
    public UserId newMember(ClanId clanId, BranchId homeBranchId, String displayName) {
        UserId user = lineage.clanOrg.newUser(displayName);
        Instant now = lineage.clanOrg.clock.instant();
        lineage.clanOrg.memberships.save(
                ClanMembership.grant(
                        lineage.clanOrg.ids.newId(), clanId, user, homeBranchId, null, now));
        lineage.clanOrg.grantLifecycle.onMembershipGranted(
                new MembershipGranted(clanId, user, homeBranchId, null, now));
        return user;
    }
}
