package app.giaky.authorization.domain;

import app.giaky.common.types.ClanId;
import app.giaky.common.types.UserId;
import java.util.List;

/**
 * A user's live grants in one clan — step 3 of the algorithm (doc 04 §4).
 *
 * <p>One indexed query behind {@code ix_grant_lookup}, and the adapter caches the answer for the
 * duration of the request (B3.6). That is what lets a single HTTP call ask {@code decide()} a dozen
 * times — once per endpoint of a relationship, once per node of a batch — without turning into a
 * dozen round trips.
 *
 * <p>Revoked and expired grants never appear here. Filtering them at the source rather than in the
 * resolver means no caller can forget, and it is why the resolver takes no clock.
 */
public interface GrantPort {

    /** Every grant that confers something on {@code user} in {@code clan} right now. */
    List<PermissionGrant> effectiveGrants(ClanId clan, UserId user);

    /**
     * Forgets anything cached for this person in this clan.
     *
     * <p>On the port rather than on the adapter, because the caching is the port's business and the
     * services that write grants must not have to know whether there is any. Without it, a request
     * that grants a permission and then re-reads the caller's own — which every grant endpoint does,
     * to return the updated screen — would answer from the list it loaded before the write.
     */
    void invalidate(ClanId clan, UserId user);
}
