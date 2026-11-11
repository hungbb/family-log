package app.giaky.linking.application;

import app.giaky.lineage.application.PersonLinkGateway;
import app.giaky.lineage.application.event.PersonRestored;
import app.giaky.linking.domain.ProfilePropagationRepository;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * A node that comes back with nobody on it comes back with nobody's choices on it either (FR-N6).
 *
 * <p>Restoring a deleted node normally restores its link with it. It does not when the account has
 * since been given another node or has left the clan — {@code PersonService.restore} brings the
 * node back unlinked rather than break I-P4 or I-P5. Lineage cannot reach the propagation flags
 * (they are this context's table), so this is where they are forgotten, for the reason {@code
 * PersonLinkService.unlink} forgets them: left behind, the next person linked to the node would
 * inherit somebody else's decision about their own name and photo.
 *
 * <p>Idempotent, and deliberately not conditional on <i>why</i> the node is unlinked: an unlinked
 * node has no business holding flags, whichever road it took to get there.
 *
 * <p>Plain {@code @EventListener}, inside the restoring transaction, like every other listener in
 * this context.
 */
@Component
public class RestoredNodeListener {

    private final PersonLinkGateway lineage;
    private final ProfilePropagationRepository propagation;

    public RestoredNodeListener(
            PersonLinkGateway lineage, ProfilePropagationRepository propagation) {
        this.lineage = lineage;
        this.propagation = propagation;
    }

    @EventListener
    public void onPersonRestored(PersonRestored event) {
        boolean linked =
                lineage.node(event.clanId(), event.personId())
                        .map(node -> node.linkedUserId() != null)
                        .orElse(false);
        if (!linked) {
            propagation.clear(event.clanId(), event.personId());
        }
    }
}
