package app.giaky.authorization.domain;

import app.giaky.common.types.ClanId;
import app.giaky.common.types.UserId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Storage for {@link PermissionGrant}.
 *
 * <p>Every read takes a {@link ClanId} (doc 02 §7 rule 4) with one annotated exception, the
 * cross-tenant sweep. That is not ceremony here of all places: a grant id is a UUID a caller
 * supplies on {@code DELETE /permissions/{grantId}}, and the clan parameter is the difference
 * between revoking a grant in this clan and revoking one in somebody else's.
 *
 * <p>{@code effectiveAt} appears on the read methods rather than being defaulted inside them,
 * because the clock is injected (doc 09 §4.6) and a grant that expires at noon must stop working at
 * noon in a test as surely as in production.
 */
public interface PermissionGrantRepository {

    /** One grant of this clan, revoked or not — revocation needs to find an already-revoked row. */
    Optional<PermissionGrant> findById(ClanId clanId, UUID grantId);

    /**
     * Everything {@code user} effectively holds in {@code clan} — the hot path, behind {@code
     * ix_grant_lookup}.
     */
    List<PermissionGrant> findEffectiveFor(ClanId clanId, UserId user, Instant effectiveAt);

    /** Every live grant in the clan, for the permissions screen (doc 05 §4.6). */
    List<PermissionGrant> findAllEffective(ClanId clanId, Instant effectiveAt);

    /**
     * Live grants of one permission in the clan.
     *
     * <p>Exists for last-admin protection (I-M3), which has to count the clan's {@code CLAN_ADMIN}
     * holders and must not do it by loading every grant the clan has ever issued.
     */
    List<PermissionGrant> findEffectiveByPermission(
            ClanId clanId, Permission permission, Instant effectiveAt);

    /** Whether an identical live grant already exists — I-G5, checked before the index refuses. */
    Optional<PermissionGrant> findEffectiveDuplicate(
            ClanId clanId,
            UserId grantee,
            Permission permission,
            PermissionScope scope,
            Instant effectiveAt);

    /**
     * The same grant, lapsed and not yet swept — the row still sitting in {@code ux_grant_active}.
     *
     * <p>Every read filters on {@code expires_at}; the unique index does not, because an index
     * predicate cannot mention the clock. So between a grant's expiry and the daily sweep there is a
     * row that confers nothing and still refuses its own replacement. Whoever renews a grant in that
     * window has to close this one first.
     */
    Optional<PermissionGrant> findLapsedDuplicate(
            ClanId clanId,
            UserId grantee,
            Permission permission,
            PermissionScope scope,
            Instant now);

    PermissionGrant save(PermissionGrant grant);

    /**
     * Grants that have lapsed but are not yet marked revoked — the nightly sweep's input (B3.14).
     *
     * <p>Cross-tenant by nature: the sweep does not belong to a clan. Reads already filter on {@code
     * expires_at}, so this changes nothing about who may do what; it keeps the table honest.
     */
    @app.giaky.common.types.TenantExempt(
            justification =
                    "The expiry sweep is a background job over every tenant. It grants nothing and"
                        + " revokes only rows whose own expiry has already passed, which every read"
                        + " path filters on regardless.")
    List<PermissionGrant> findLapsedNotYetRevoked(Instant now, int limit);
}
