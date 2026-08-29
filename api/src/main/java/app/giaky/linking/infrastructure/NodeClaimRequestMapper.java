package app.giaky.linking.infrastructure;

import app.giaky.linking.domain.NodeClaimRequest;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * {@link NodeClaimRequest} ↔ {@link NodeClaimRequestJpaEntity}.
 *
 * <p>{@code cancel(Instant)} is ignored for the reason the other mappers ignore theirs: MapStruct
 * reads a one-argument method returning its own type as a setter, and this one is the state machine
 * rather than state.
 */
@Mapper
public interface NodeClaimRequestMapper {

    NodeClaimRequestJpaEntity toEntity(NodeClaimRequest claim);

    @Mapping(target = "cancel", ignore = true)
    NodeClaimRequest toDomain(NodeClaimRequestJpaEntity entity);
}
