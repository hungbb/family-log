package app.giaky.linking.application;

import app.giaky.common.types.ClanId;
import app.giaky.common.types.PersonId;
import app.giaky.identity.application.event.ProfileUpdated;
import app.giaky.lineage.application.PersonLinkGateway;
import app.giaky.linking.domain.ProfilePropagationRepository;
import app.giaky.linking.domain.PropagationFlags;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Opt-in profile propagation (FR-N6, B6.8).
 *
 * <p>When an account changes its display name or photo, the nodes that person is linked to pick up
 * whichever of those fields they have opted in to — <b>and no others, on <i>no</i> node that has not
 * opted in</b>. The default is off everywhere, so the overwhelming majority of profile edits do
 * nothing here at all.
 *
 * <p>A listener rather than a call from {@code AccountService}, for the same reason {@code
 * GrantLifecycleListener} is one: Identity should not know that Linking exists, and this phase
 * should be an addition to Phase 1's code rather than an edit to it.
 *
 * <p><b>Plain {@code @EventListener}, inside the publisher's transaction.</b> The profile and the
 * nodes it propagates to should move together — a commit that saved the account and lost the copy
 * would leave the two disagreeing with nothing to reconcile them until the next edit.
 */
@Component
public class ProfilePropagationListener {

    private static final Logger log = LoggerFactory.getLogger(ProfilePropagationListener.class);

    private final PersonLinkGateway lineage;
    private final ProfilePropagationRepository propagation;

    public ProfilePropagationListener(
            PersonLinkGateway lineage, ProfilePropagationRepository propagation) {
        this.lineage = lineage;
        this.propagation = propagation;
    }

    @EventListener
    public void onProfileUpdated(ProfileUpdated event) {
        int copied = 0;
        for (PersonLinkGateway.NodeRef node : lineage.nodesLinkedTo(event.userId())) {
            ClanId clanId = node.clanId();
            PersonId personId = node.id();
            PropagationFlags flags = propagation.find(clanId, personId);
            if (!flags.anyEnabled()) {
                continue;
            }
            lineage.propagateProfile(
                    clanId,
                    personId,
                    event.userId(),
                    flags.avatar(),
                    event.avatarMediaId(),
                    flags.displayName(),
                    event.displayName());
            copied++;
        }
        if (copied > 0) {
            log.info("Propagated profile to {} linked node(s) userId={}", copied, event.userId());
        }
    }
}
