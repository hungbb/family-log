package app.giaky.authorization.application;

import app.giaky.clanorg.application.LastAdminProtection;
import app.giaky.common.error.ErrorCode;
import app.giaky.common.error.PermissionDeniedException;
import app.giaky.common.types.ClanId;
import app.giaky.common.types.UserId;
import org.springframework.stereotype.Component;

/**
 * I-M3, with the real definition of "administrator" (doc 04 §6).
 *
 * <p>Phase 2 answered this question from {@code clans.created_by}, which was blunt but safe: it
 * could only refuse a removal that a grant model might have allowed, never allow one that would
 * strand a clan. Now the question has an honest answer — a clan is administered by whoever holds a
 * live {@code CLAN_ADMIN} grant <i>and</i> an active membership — and this is the whole of the
 * change. Every call site in ClanOrg keeps working, which is what the port was for.
 *
 * <p>The rule guards all three routes out of the job at once, because they are the same event seen
 * from different angles: revoking the grant, removing the member, and the member leaving. The fourth
 * — the grant simply expiring — is why {@code CLAN_ADMIN} grants issued through the API should carry
 * no expiry, and why the sweep is hygiene rather than enforcement.
 */
@Component
public class GrantBackedLastAdminProtection implements LastAdminProtection {

    private final GrantService grants;

    public GrantBackedLastAdminProtection(GrantService grants) {
        this.grants = grants;
    }

    @Override
    public void requireNotLastAdmin(ClanId clanId, UserId userId) {
        if (grants.isFinalAdministrator(clanId, userId)) {
            throw new PermissionDeniedException(
                    ErrorCode.LAST_ADMIN_PROTECTED,
                    "This clan would be left with nobody able to administer it. Make somebody else"
                            + " an administrator first.");
        }
    }
}
