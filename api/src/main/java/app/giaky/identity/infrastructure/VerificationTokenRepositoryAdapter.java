package app.giaky.identity.infrastructure;

import app.giaky.common.types.UserId;
import app.giaky.identity.domain.TokenPurpose;
import app.giaky.identity.domain.VerificationToken;
import app.giaky.identity.domain.VerificationTokenRepository;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** The JPA side of {@link VerificationTokenRepository}. */
@Repository
public class VerificationTokenRepositoryAdapter implements VerificationTokenRepository {

    private final VerificationTokenJpaRepository repository;
    private final VerificationTokenMapper mapper;

    public VerificationTokenRepositoryAdapter(
            VerificationTokenJpaRepository repository, VerificationTokenMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public Optional<VerificationToken> findByTokenHash(String tokenHash) {
        return repository.findByTokenHash(tokenHash).map(mapper::toDomain);
    }

    @Override
    public VerificationToken save(VerificationToken token) {
        return mapper.toDomain(repository.save(mapper.toEntity(token)));
    }

    @Override
    public int consumeOutstanding(UserId userId, TokenPurpose purpose, Instant now) {
        return repository.consumeOutstanding(userId.value(), purpose.name(), now);
    }
}
