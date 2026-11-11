package app.giaky.linking.application;

import app.giaky.clanorg.application.event.MembershipRevoked;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * A place in the tree does not outlive the membership it was given to (I-P5, I-K2).
 *
 * <p>{@code PersonUserLinker} refuses to link somebody who is not an active member, and that is the
 * whole of I-P5 on the way in. This is the way out: when a member is removed or leaves, the node
 * they held goes back to the unlinked pool and the claims they still had open are withdrawn. Without
 * it the tree keeps saying "this is her" about an account that can no longer open the clan, nobody
 * else can claim the node until an approver notices, and somebody re-admitted later finds themselves
 * already seated — holding the self-service edit of doc 04 §6 — before anyone has looked at them
 * again.
 *
 * <p>A listener for the reason {@code GrantLifecycleListener} is one, and its mirror image: ClanOrg
 * published {@code MembershipRevoked} so that later phases could react to it without editing {@code
 * MembershipService}, and Phase 6 never subscribed.
 *
 * <p><b>Plain {@code @EventListener}, not {@code @TransactionalEventListener}.</b> It runs inside
 * the transaction that ended the membership. A removal that committed and an unlink that did not
 * would leave exactly the state this class exists to prevent, with nothing to retry it.
 */
@Component
public class LinkLifecycleListener {

    private static final Logger log = LoggerFactory.getLogger(LinkLifecycleListener.class);

    private final PersonLinkService links;
    private final ClaimService claims;

    public LinkLifecycleListener(PersonLinkService links, ClaimService claims) {
        this.links = links;
        this.claims = claims;
    }

    @EventListener
    public void onMembershipRevoked(MembershipRevoked event) {
        boolean released =
                links.releaseNodeOf(
                        event.clanId(),
                        event.userId(),
                        event.actorUserId(),
                        event.selfInitiated());
        int withdrawn = claims.withdrawAllOf(event.clanId(), event.userId(), event.actorUserId());

        if (released || withdrawn > 0) {
            log.info(
                    "Membership ended: node released={} pending claims withdrawn={} clanId={}"
                            + " userId={}",
                    released,
                    withdrawn,
                    event.clanId(),
                    event.userId());
        }
    }
}
