package app.giaky.identity.support;

import app.giaky.common.types.UserId;
import app.giaky.identity.domain.RefreshToken;
import app.giaky.identity.domain.RefreshToken.RevocationReason;
import app.giaky.identity.domain.RefreshTokenRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** A {@link RefreshTokenRepository} backed by a map, including the two bulk revocations. */
public final class InMemoryRefreshTokenRepository implements RefreshTokenRepository {

    private final Map<UUID, RefreshToken> byId = new LinkedHashMap<>();

    @Override
    public Optional<RefreshToken> findByTokenHash(String tokenHash) {
        return byId.values().stream()
                .filter(token -> token.tokenHash().equals(tokenHash))
                .findFirst();
    }

    @Override
    public RefreshToken save(RefreshToken token) {
        byId.put(token.id(), token);
        return token;
    }

    @Override
    public List<RefreshToken> findActiveByUser(UserId userId, Instant now) {
        return byId.values().stream()
                .filter(token -> token.userId().equals(userId))
                .filter(token -> token.isUsableAt(now))
                .sorted(Comparator.comparing(RefreshToken::issuedAt).reversed())
                .toList();
    }

    @Override
    public int revokeChain(UUID chainId, RevocationReason reason, Instant now) {
        return revokeMatching(token -> token.chainId().equals(chainId), reason, now);
    }

    @Override
    public int revokeAllForUser(UserId userId, RevocationReason reason, Instant now) {
        return revokeMatching(token -> token.userId().equals(userId), reason, now);
    }

    /** Every token ever stored, revoked or not. For asserting on what a revocation actually did. */
    public List<RefreshToken> all() {
        return List.copyOf(byId.values());
    }

    private int revokeMatching(
            java.util.function.Predicate<RefreshToken> matches,
            RevocationReason reason,
            Instant now) {

        List<RefreshToken> live =
                new ArrayList<>(
                        byId.values().stream().filter(matches).filter(t -> !t.isRevoked()).toList());
        live.forEach(token -> byId.put(token.id(), token.revoke(reason, now)));
        return live.size();
    }
}
