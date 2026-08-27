package app.giaky.clanorg.infrastructure;

import app.giaky.clanorg.domain.ClanMembership;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * {@link ClanMembership} ↔ {@link ClanMembershipJpaEntity}.
 *
 * <p>No value-object helpers: every field is already a type both sides share, and the status enum
 * is stored as its own constant name. The two ignores are the fluent-setter false positives
 * described in {@code ClanMapper} — {@code leave(Instant)} and {@code remove(Instant)} are
 * transitions, not columns.
 */
@Mapper
public interface ClanMembershipMapper {

    ClanMembershipJpaEntity toEntity(ClanMembership membership);

    @Mapping(target = "leave", ignore = true)
    @Mapping(target = "remove", ignore = true)
    ClanMembership toDomain(ClanMembershipJpaEntity entity);
}
