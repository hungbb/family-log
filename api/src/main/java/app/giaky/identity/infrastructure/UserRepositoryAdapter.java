package app.giaky.identity.infrastructure;

import app.giaky.common.types.Paged;
import app.giaky.common.types.UserId;
import app.giaky.identity.domain.EmailAddress;
import app.giaky.identity.domain.User;
import app.giaky.identity.domain.UserRepository;
import app.giaky.identity.domain.UserStatus;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

/**
 * The JPA side of {@link UserRepository}.
 *
 * <p>Thin by design: it maps and delegates, and holds no logic of its own. Anything that looks like
 * a decision here belongs in the application service, where it is testable without a database.
 */
@Repository
public class UserRepositoryAdapter implements UserRepository {

    /** Candidate ids per search statement, well inside PostgreSQL's bind-parameter limit. */
    static final int SEARCH_SLICE = 1_000;

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
    public List<User> findAllById(Collection<UserId> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        List<UUID> raw = ids.stream().map(UserId::value).toList();
        return repository.findByIdIn(raw).stream().map(mapper::toDomain).toList();
    }

    @Override
    public List<UserId> searchIdsAmong(String query, Collection<UserId> among) {
        // In slices: PostgreSQL's wire protocol takes at most 32,767 bind parameters per statement,
        // and a clan's roll has no cap of its own.
        List<UUID> raw = among.stream().map(UserId::value).distinct().toList();
        List<UserId> matches = new ArrayList<>();
        for (int from = 0; from < raw.size(); from += SEARCH_SLICE) {
            List<UUID> slice = raw.subList(from, Math.min(from + SEARCH_SLICE, raw.size()));
            repository.searchIdsAmong(query, slice).stream().map(UserId::of).forEach(matches::add);
        }
        return matches;
    }

    @Override
    public Paged<User> searchAll(String query, UserStatus status, int page, int size) {
        Page<UserJpaEntity> found =
                repository.searchAll(
                        query == null ? "" : query.trim(),
                        status == null ? "" : status.name(),
                        PageRequest.of(
                                page,
                                size,
                                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("id"))));
        return new Paged<>(
                found.getContent().stream().map(mapper::toDomain).toList(),
                found.getNumber(),
                found.getSize(),
                found.getTotalElements());
    }

    @Override
    public User save(User user) {
        return mapper.toDomain(repository.save(mapper.toEntity(user)));
    }
}
