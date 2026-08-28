package app.giaky.clanorg.application;

import app.giaky.common.types.ClanId;
import app.giaky.common.types.UserId;

/**
 * Refuses to leave a clan without an administrator (I-M3).
 *
 * <p>A port, not an implementation, because the honest answer to "is this the last admin?" needs the
 * grant table, which lives in another context. Phase 2 satisfied it with the crude rule it had —
 * {@code clans.created_by} — and Phase 3 replaced that with {@code GrantBackedLastAdminProtection},
 * which counts live {@code CLAN_ADMIN} grants. Every call site here kept working, which is what the
 * port was for.
 *
 * <p>A membership removal that forgets to ask this question is how a clan ends up with nobody who
 * can approve anything, and adding the question later means finding every place that removes a
 * membership.
 */
public interface LastAdminProtection {

    /**
     * Throws when removing {@code userId} from {@code clanId} would leave the clan unadministered.
     *
     * @throws app.giaky.common.error.PermissionDeniedException with {@code LAST_ADMIN_PROTECTED}
     */
    void requireNotLastAdmin(ClanId clanId, UserId userId);
}
