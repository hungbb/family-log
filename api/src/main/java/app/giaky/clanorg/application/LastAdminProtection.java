package app.giaky.clanorg.application;

import app.giaky.common.types.ClanId;
import app.giaky.common.types.UserId;

/**
 * Refuses to leave a clan without an administrator (I-M3).
 *
 * <p>A port, not an implementation, because the honest answer to "is this the last admin?" needs the
 * grant table, and grants arrive in Phase 3. Phase 2 satisfies it with the crude rule it has —
 * {@code clans.created_by} — inside {@code InterimClanAccessCheck}; Phase 3 replaces that
 * implementation and every call site here keeps working.
 *
 * <p>The interface exists now rather than then so the call sites are written once. A membership
 * removal that forgets to ask this question is how a clan ends up with nobody who can approve
 * anything, and adding the question later means finding every place that removes a membership.
 */
public interface LastAdminProtection {

    /**
     * Throws when removing {@code userId} from {@code clanId} would leave the clan unadministered.
     *
     * @throws app.giaky.common.error.PermissionDeniedException with {@code LAST_ADMIN_PROTECTED}
     */
    void requireNotLastAdmin(ClanId clanId, UserId userId);
}
