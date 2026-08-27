package app.giaky.clanorg.infrastructure;

import app.giaky.clanorg.domain.ClanJoinRequest;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * {@link ClanJoinRequest} ↔ {@link ClanJoinRequestJpaEntity}.
 *
 * <p>{@code cancel(Instant)} and {@code expire(Instant)} are ignored for the same reason the other
 * mappers ignore theirs: MapStruct reads a one-argument method returning its own type as a setter,
 * and these are the state machine, not state.
 */
@Mapper
public interface ClanJoinRequestMapper {

    ClanJoinRequestJpaEntity toEntity(ClanJoinRequest request);

    @Mapping(target = "cancel", ignore = true)
    @Mapping(target = "expire", ignore = true)
    ClanJoinRequest toDomain(ClanJoinRequestJpaEntity entity);
}
