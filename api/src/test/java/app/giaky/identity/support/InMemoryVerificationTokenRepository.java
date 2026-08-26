package app.giaky.identity.support;

import app.giaky.common.types.UserId;
import app.giaky.identity.domain.TokenPurpose;
import app.giaky.identity.domain.VerificationToken;
import app.giaky.identity.domain.VerificationTokenRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** A {@link VerificationTokenRepository} backed by a map. */
public final class InMemoryVerificationTokenRepository implements VerificationTokenRepository {

    private final Map<UUID, VerificationToken> byId = new LinkedHashMap<>();

    @Override
    public Optional<VerificationToken> findByTokenHash(String tokenHash) {
        return byId.values().stream()
                .filter(token -> token.tokenHash().equals(tokenHash))
                .findFirst();
    }

    @Override
    public VerificationToken save(VerificationToken token) {
        byId.put(token.id(), token);
        return token;
    }

    @Override
    public int consumeOutstanding(UserId userId, TokenPurpose purpose, Instant now) {
        List<VerificationToken> live =
                new ArrayList<>(
                        byId.values().stream()
                                .filter(token -> token.userId().equals(userId))
                                .filter(token -> token.purpose() == purpose)
                                .filter(token -> !token.isConsumed())
                                .toList());
        live.forEach(token -> byId.put(token.id(), token.consume(now)));
        return live.size();
    }

    public List<VerificationToken> all() {
        return List.copyOf(byId.values());
    }
}
