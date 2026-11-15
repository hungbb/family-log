package app.giaky.identity.infrastructure;

import app.giaky.common.types.UserId;
import app.giaky.identity.domain.EmailAddress;
import app.giaky.identity.domain.LocaleTag;
import app.giaky.identity.domain.PasswordHash;
import app.giaky.identity.domain.User;
import java.util.UUID;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * {@link User} ↔ {@link UserJpaEntity}.
 *
 * <p>MapStruct generates the field copying; the {@code default} methods below teach it the value
 * objects. Written this way rather than by hand because the compiler then checks the mapping is
 * total — the build is configured with {@code unmappedTargetPolicy=ERROR}, so adding a column and
 * forgetting to map it fails compilation instead of silently writing a null.
 *
 * <p>{@link LocaleTag} needs an explicit pair because it is stored as its tag ({@code vi-VN}), not
 * as the constant name MapStruct would use for an enum by default. {@code UserStatus} does use the
 * constant name, so it needs nothing.
 */
@Mapper
public interface UserMapper {

    UserJpaEntity toEntity(User user);

    /**
     * The {@code ignore}s are not unmapped columns.
     *
     * <p>MapStruct treats any single-argument method that returns its own declaring type as a fluent
     * setter, and {@code User}'s transitions — {@code verifyEmail(now)}, {@code recordLogin(now)},
     * {@code revokeAllSessions(now)}, {@code suspend(now)}, {@code reactivate(now)} — have exactly
     * that shape. They are behaviour, not state, and
     * the record's components are all mapped through its canonical constructor. Ignoring them by
     * name keeps {@code unmappedTargetPolicy=ERROR} switched on for everything that <i>is</i> state.
     */
    @Mapping(target = "verifyEmail", ignore = true)
    @Mapping(target = "recordLogin", ignore = true)
    @Mapping(target = "revokeAllSessions", ignore = true)
    @Mapping(target = "suspend", ignore = true)
    @Mapping(target = "reactivate", ignore = true)
    User toDomain(UserJpaEntity entity);

    default UUID fromUserId(UserId value) {
        return value == null ? null : value.value();
    }

    default UserId toUserId(UUID value) {
        return value == null ? null : UserId.of(value);
    }

    default String fromEmailAddress(EmailAddress value) {
        return value == null ? null : value.value();
    }

    default EmailAddress toEmailAddress(String value) {
        return value == null ? null : EmailAddress.of(value);
    }

    default String fromPasswordHash(PasswordHash value) {
        return value == null ? null : value.value();
    }

    default PasswordHash toPasswordHash(String value) {
        return value == null ? null : PasswordHash.of(value);
    }

    default String fromLocaleTag(LocaleTag value) {
        return value == null ? null : value.tag();
    }

    default LocaleTag toLocaleTag(String value) {
        return value == null ? LocaleTag.DEFAULT : LocaleTag.fromTagOrDefault(value);
    }
}
