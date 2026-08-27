package app.giaky.clanorg.infrastructure;

import app.giaky.common.types.TenantExempt;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data access to {@code clans}.
 *
 * <p>Type-level {@code @TenantExempt}, and it is not a shortcut: this aggregate <i>is</i> the
 * tenant. Its primary key is the tenancy key, so a read by id is already scoped as tightly as any
 * read can be, and a {@code ClanId} parameter alongside it would be the same value twice.
 *
 * <p>The one genuinely cross-tenant read is the code lookup, which is cross-tenant by design: a
 * person joining a clan is by definition not a member of it yet (FR-J1).
 */
@TenantExempt(
        justification =
                "The clan aggregate is the tenant: `clans.id` is the tenancy key, so every read"
                    + " here is scoped by its own primary key. The code lookup is deliberately"
                    + " cross-tenant — a joining user holds a code and no membership (FR-J1) — and"
                    + " returns only the minimal card of doc 05 §4.3.")
public interface ClanJpaRepository extends JpaRepository<ClanJpaEntity, UUID> {

    Optional<ClanJpaEntity> findByIdAndDeletedAtIsNull(UUID id);

    Optional<ClanJpaEntity> findByClanCodeAndDeletedAtIsNull(String clanCode);

    /**
     * Whether a code has <i>ever</i> been issued.
     *
     * <p>No soft-delete predicate, deliberately. I-C1 says a code is never reissued, so a deleted
     * clan keeps its code reserved — otherwise a code shared last year could later resolve to a
     * different family.
     */
    boolean existsByClanCode(String clanCode);

    List<ClanJpaEntity> findByIdInAndDeletedAtIsNull(Collection<UUID> ids);
}
