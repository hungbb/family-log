package app.giaky.clanorg.infrastructure;

import app.giaky.common.types.ClanId;
import app.giaky.common.types.TenantExempt;
import app.giaky.common.types.UserId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring Data access to {@code clan_memberships}. */
public interface ClanMembershipJpaRepository extends JpaRepository<ClanMembershipJpaEntity, UUID> {

    Optional<ClanMembershipJpaEntity> findByClanIdAndUserIdAndStatus(
            ClanId clanId, UserId userId, String status);

    List<ClanMembershipJpaEntity> findByClanIdAndStatusOrderByJoinedAtDesc(
            ClanId clanId, String status);

    @TenantExempt(
            justification =
                    "The clan switcher (FR-C8) asks which tenants a user belongs to, so it cannot"
                        + " presuppose one. The user id comes from the access token, never from the"
                        + " request, and the answer is exactly the set of clans this caller is"
                        + " already a member of.")
    List<ClanMembershipJpaEntity> findByUserIdAndStatusOrderByJoinedAtDesc(
            UserId userId, String status);

    /**
     * Member counts per branch, in one grouped query rather than one query per branch.
     *
     * <p>Returns rows of {@code [BranchId, Long]}; the adapter shapes them. A projection interface
     * would read better and would depend on how a native alias is cased, which is a fight not worth
     * having for two columns.
     */
    @Query(
            """
            select m.homeBranchId, count(m)
            from ClanMembershipJpaEntity m
            where m.clanId = :clanId
              and m.status = 'ACTIVE'
              and m.homeBranchId is not null
            group by m.homeBranchId
            """)
    List<Object[]> countActiveMembersPerBranch(@Param("clanId") ClanId clanId);
}
