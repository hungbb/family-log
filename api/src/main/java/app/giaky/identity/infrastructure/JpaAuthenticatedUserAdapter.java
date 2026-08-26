package app.giaky.identity.infrastructure;

import app.giaky.common.security.AuthenticatedUser;
import app.giaky.common.security.AuthenticatedUserPort;
import app.giaky.common.types.UserId;
import app.giaky.identity.domain.UserStatus;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Identity's answer to "who is this token for, and may they still act?" (I-U2).
 *
 * <p>Reads the entity directly rather than going through {@code UserRepository}: the filter needs
 * three columns per request, and mapping a whole aggregate — including a BCrypt hash it must never
 * see — to discard all but the identity would be work done for nothing.
 *
 * <p>No {@code @Transactional} here: the transaction boundary is the application service (doc 09
 * §2.2), and this runs in the filter chain where there is no use case to bound. The single read
 * gets Spring Data's own transaction, which is all it needs.
 */
@Component
public class JpaAuthenticatedUserAdapter implements AuthenticatedUserPort {

    private final UserJpaRepository repository;

    public JpaAuthenticatedUserAdapter(UserJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<AuthenticatedUser> findAuthenticatable(UserId userId) {
        return repository
                .findById(userId.value())
                .filter(entity -> statusOf(entity).canAuthenticate())
                .map(
                        entity ->
                                new AuthenticatedUser(
                                        UserId.of(entity.getId()),
                                        entity.getEmail(),
                                        entity.getTokenVersion()));
    }

    /**
     * An unrecognised status is treated as unable to authenticate.
     *
     * <p>Fail closed: a value this build does not know about was written by a newer one, and
     * guessing that it is harmless is the wrong way round.
     */
    private static UserStatus statusOf(UserJpaEntity entity) {
        try {
            return UserStatus.valueOf(entity.getStatus());
        } catch (IllegalArgumentException unknown) {
            return UserStatus.SUSPENDED;
        }
    }
}
