package app.giaky.identity.infrastructure;

import app.giaky.common.types.UserId;
import app.giaky.identity.domain.EmailAddress;
import app.giaky.identity.domain.User;
import app.giaky.identity.domain.UserRepository;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * The JPA side of {@link UserRepository}.
 *
 * <p>Thin by design: it maps and delegates, and holds no logic of its own. Anything that looks like
 * a decision here belongs in the application service, where it is testable without a database.
 */
@Repository
public class UserRepositoryAdapter implements UserRepository {

    private final UserJpaRepository repository;
    private final UserMapper mapper;

    public UserRepositoryAdapter(UserJpaRepository repository, UserMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public Optional<User> findById(UserId id) {
        return repository.findById(id.value()).map(mapper::toDomain);
    }

    @Override
    public Optional<User> findByEmail(EmailAddress email) {
        return repository.findByEmail(email.value()).map(mapper::toDomain);
    }

    @Override
    public boolean existsByEmail(EmailAddress email) {
        return repository.existsByEmail(email.value());
    }

    @Override
    public User save(User user) {
        return mapper.toDomain(repository.save(mapper.toEntity(user)));
    }
}
