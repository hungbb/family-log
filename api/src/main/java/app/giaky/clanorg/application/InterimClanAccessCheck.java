package app.giaky.clanorg.application;

import app.giaky.clanorg.domain.Clan;
import app.giaky.clanorg.domain.ClanMembership;
import app.giaky.clanorg.domain.ClanMembershipRepository;
import app.giaky.clanorg.domain.ClanRepository;
import app.giaky.common.error.ErrorCode;
import app.giaky.common.error.NotFoundException;
import app.giaky.common.error.PermissionDeniedException;
import app.giaky.common.types.ClanId;
import app.giaky.common.types.UserId;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * <b>The whole of Phase 2's authorization, in one deliberately crude class.</b>
 *
 * <p>Two rules and no more:
 *
 * <ul>
 *   <li><b>Read</b> — an active membership in the clan.
 *   <li><b>Administer</b> — being the account that created the clan.
 * </ul>
 *
 * <p>That is not the model the product wants. Doc 04 describes grants, scopes and a resolver, and
 * Phase 3 builds them. This exists because Phase 2 has to ship clans, branches and joining <i>with</i>
 * an access rule rather than without one, and because the alternative — scattering "is the caller
 * the creator?" across six services — is a refactor nobody would ever finish.
 *
 * <p>So it is one class, it is {@code @Deprecated}, and it is the only place in the codebase that
 * decides anything about access to a clan. Phase 3's job is to replace its two methods with
 * {@code PermissionResolver.require(...)} and delete the file; the exit criterion for this phase is
 * that doing so touches nothing else.
 *
 * <p><b>404 versus 403</b> (doc 04 §7). A caller with no membership is told the clan does not exist,
 * because a private clan's existence is not public information and clan codes must not be
 * enumerable. A caller who <i>is</i> a member already knows it exists, so they get a precise 403.
 * Every method below preserves that distinction, and {@code ClanAccessIT} asserts it.
 */
@Deprecated(since = "Phase 2 — replaced by PermissionResolver in Phase 3", forRemoval = true)
@Component
public class InterimClanAccessCheck implements LastAdminProtection {

    /**
     * What the founder is told they hold, for the UI to gate on.
     *
     * <p>The names come from doc 02 §2.8 so that {@code myPermissions[]} has the shape Phase 3 will
     * fill in properly. Only the <i>source</i> of the list changes then; the contract does not.
     */
    private static final List<String> CREATOR_PERMISSIONS =
            List.of(
                    "CLAN_VIEW",
                    "TREE_EDIT",
                    "MEMBER_APPROVE",
                    "NODE_CLAIM_APPROVE",
                    "ROLE_MANAGE",
                    "CLAN_ADMIN");

    /** What every other member holds until Phase 3: they can look. */
    private static final List<String> MEMBER_PERMISSIONS = List.of("CLAN_VIEW");

    private final ClanRepository clans;
    private final ClanMembershipRepository memberships;

    public InterimClanAccessCheck(ClanRepository clans, ClanMembershipRepository memberships) {
        this.clans = clans;
        this.memberships = memberships;
    }

    /**
     * The clan, if the caller may see it at all.
     *
     * @throws NotFoundException the clan does not exist, is deleted, or the caller is not a member —
     *     three cases that must be indistinguishable from outside (doc 04 §7)
     */
    public Clan requireVisible(UserId caller, ClanId clanId) {
        Clan clan = clans.findById(clanId).orElseThrow(InterimClanAccessCheck::notFound);
        if (memberships.findActive(clanId, caller).isEmpty()) {
            throw notFound();
        }
        return clan;
    }

    /**
     * The clan, if the caller may administer it.
     *
     * @throws NotFoundException the caller is not a member
     * @throws PermissionDeniedException the caller is a member but did not create the clan
     */
    public Clan requireAdministrator(UserId caller, ClanId clanId) {
        Clan clan = requireVisible(caller, clanId);
        requireAdministrator(caller, clan);
        return clan;
    }

    /** {@link #requireAdministrator(UserId, ClanId)} for a clan the caller has already loaded. */
    public void requireAdministrator(UserId caller, Clan clan) {
        if (!clan.isCreatedBy(caller)) {
            throw new PermissionDeniedException(
                    "Only the person who created this clan can change it. Finer-grained roles"
                            + " arrive in the next release.");
        }
    }

    /** The caller's active membership, or empty. Used where absence is an answer rather than an error. */
    public Optional<ClanMembership> activeMembership(UserId caller, ClanId clanId) {
        return memberships.findActive(clanId, caller);
    }

    /** Whether the caller may see the clan, without throwing. */
    public boolean isMember(UserId caller, ClanId clanId) {
        return memberships.findActive(clanId, caller).isPresent();
    }

    /**
     * The permission names to send a member for UI gating (doc 05 §4.3).
     *
     * <p>UI gating is a convenience and never the boundary (doc 07 §8): everything in this list is
     * re-decided server-side by the two methods above.
     */
    public List<String> permissionsOf(UserId caller, Clan clan) {
        return clan.isCreatedBy(caller) ? CREATOR_PERMISSIONS : MEMBER_PERMISSIONS;
    }

    /**
     * I-M3, with the only definition of "admin" this phase has.
     *
     * <p>The founder is the clan's single administrator, so they may not leave and may not be
     * removed. Blunt, and correct in the one direction that matters: it can only refuse a removal
     * that Phase 3 might have allowed, never allow one that would strand a clan.
     */
    @Override
    public void requireNotLastAdmin(ClanId clanId, UserId userId) {
        Clan clan = clans.findById(clanId).orElseThrow(InterimClanAccessCheck::notFound);
        if (clan.isCreatedBy(userId)) {
            throw new PermissionDeniedException(
                    ErrorCode.LAST_ADMIN_PROTECTED,
                    "This clan would be left with nobody able to administer it.");
        }
    }

    private static NotFoundException notFound() {
        // Deliberately says nothing about which of the three cases this is.
        return new NotFoundException(
                ErrorCode.CLAN_NOT_FOUND, "That clan does not exist, or you are not a member of it.");
    }
}
