package app.giaky.identity.infrastructure;

import app.giaky.common.types.UserId;
import app.giaky.identity.domain.RefreshToken;
import java.util.UUID;
import org.mapstruct.Mapper;

/** {@link RefreshToken} ↔ {@link RefreshTokenJpaEntity}. */
@Mapper
public interface RefreshTokenMapper {

    RefreshTokenJpaEntity toEntity(RefreshToken token);

    RefreshToken toDomain(RefreshTokenJpaEntity entity);

    default UUID fromUserId(UserId value) {
        return value == null ? null : value.value();
    }

    default UserId toUserId(UUID value) {
        return value == null ? null : UserId.of(value);
    }
}
