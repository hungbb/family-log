package app.giaky.identity.infrastructure;

import app.giaky.common.types.UserId;
import app.giaky.identity.domain.RefreshToken;
import app.giaky.identity.domain.RefreshToken.RevocationReason;
import app.giaky.identity.domain.RefreshTokenRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/** The JPA side of {@link RefreshTokenRepository}. */
@Repository
public class RefreshTokenRepositoryAdapter implements RefreshTokenRepository {

    private final RefreshTokenJpaRepository repository;
    private final RefreshTokenMapper mapper;

    public RefreshTokenRepositoryAdapter(
            RefreshTokenJpaRepository repository, RefreshTokenMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public Optional<RefreshToken> findByTokenHash(String tokenHash) {
        return repository.findByTokenHash(tokenHash).map(mapper::toDomain);
    }

    @Override
    public RefreshToken save(RefreshToken token) {
        return mapper.toDomain(repository.save(mapper.toEntity(token)));
    }

    @Override
    public List<RefreshToken> findActiveByUser(UserId userId, Instant now) {
        return repository.findActiveByUser(userId.value(), now).stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public int revokeChain(UUID chainId, RevocationReason reason, Instant now) {
        return repository.revokeChain(chainId, reason.name(), now);
    }

    @Override
    public int revokeAllForUser(UserId userId, RevocationReason reason, Instant now) {
        return repository.revokeAllForUser(userId.value(), reason.name(), now);
    }
}
