package app.giaky.clanorg.domain;

import app.giaky.common.types.ClanId;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Storage for {@link Clan} aggregates.
 *
 * <p>Every read here is already tenant-scoped by construction: this aggregate <i>is</i> the tenant,
 * so its primary key is the tenancy key. That is why the adapter's Spring Data interface carries a
 * type-level {@code @TenantExempt} rather than a per-method one — see {@code ClanJpaRepository}.
 *
 * <p>Soft-deleted clans are invisible to all of these. A deleted clan is one nobody may reach, and
 * "unless you pass a flag" is how that turns into a bug.
 */
public interface ClanRepository {

    Optional<Clan> findById(ClanId id);

    /** The join-by-code lookup (FR-J1). Deliberately unscoped: nobody is a member yet. */
    Optional<Clan> findByClanCode(ClanCode code);

    /**
     * Whether a code is already spoken for — including by a deleted clan.
     *
     * <p>The collision check behind {@code CodeGenerator}. It ignores {@code deleted_at} on purpose:
     * codes are never reissued (I-C1), so "taken" has to mean "has ever been taken".
     */
    boolean isClanCodeTaken(String code);

    /** The clans behind a set of memberships, in one query, for the switcher (FR-C8). */
    List<Clan> findAllById(Collection<ClanId> ids);

    Clan save(Clan clan);
}
