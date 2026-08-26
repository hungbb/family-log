package app.giaky.identity.infrastructure;

import app.giaky.common.types.UserId;
import app.giaky.identity.domain.VerificationToken;
import java.util.UUID;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/** {@link VerificationToken} ↔ {@link VerificationTokenJpaEntity}. */
@Mapper
public interface VerificationTokenMapper {

    VerificationTokenJpaEntity toEntity(VerificationToken token);

    /** {@code consume} is a transition, not a column; see the note in {@code UserMapper}. */
    @Mapping(target = "consume", ignore = true)
    VerificationToken toDomain(VerificationTokenJpaEntity entity);

    default UUID fromUserId(UserId value) {
        return value == null ? null : value.value();
    }

    default UserId toUserId(UUID value) {
        return value == null ? null : UserId.of(value);
    }
}
