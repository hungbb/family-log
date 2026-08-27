package app.giaky.clanorg.infrastructure;

import app.giaky.clanorg.domain.Branch;
import app.giaky.clanorg.domain.BranchCode;
import app.giaky.clanorg.domain.MaterializedPath;
import app.giaky.common.types.BranchId;
import java.util.UUID;
import org.mapstruct.Mapper;

/**
 * {@link Branch} ↔ {@link BranchJpaEntity}.
 *
 * <p>{@code path} crosses as a string and comes back as a {@link MaterializedPath}, whose
 * constructor re-validates the shape. That means a row hand-edited into an illegal path fails on
 * read rather than propagating into a subtree query — which is the whole reason the value object
 * exists instead of a {@code String} field.
 */
@Mapper
public interface BranchMapper {

    BranchJpaEntity toEntity(Branch branch);

    Branch toDomain(BranchJpaEntity entity);

    default UUID fromBranchId(BranchId value) {
        return value == null ? null : value.value();
    }

    default BranchId toBranchId(UUID value) {
        return value == null ? null : BranchId.of(value);
    }

    default String fromBranchCode(BranchCode value) {
        return value == null ? null : value.value();
    }

    default BranchCode toBranchCode(String value) {
        return value == null ? null : BranchCode.of(value);
    }

    default String fromPath(MaterializedPath value) {
        return value == null ? null : value.value();
    }

    default MaterializedPath toPath(String value) {
        return value == null ? null : MaterializedPath.of(value);
    }
}
