package app.giaky.identity.support;

import app.giaky.identity.domain.EmailAddress;
import app.giaky.identity.domain.TokenPurpose;
import app.giaky.identity.domain.VerificationTokenDelivery;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Captures the raw tokens handed to delivery, so a test can complete the flow they belong to. */
public final class RecordingTokenDelivery implements VerificationTokenDelivery {

    private final List<Delivered> delivered = new ArrayList<>();

    @Override
    public void deliver(
            EmailAddress recipient, TokenPurpose purpose, String rawToken, Instant expiresAt) {
        delivered.add(new Delivered(recipient, purpose, rawToken, expiresAt));
    }

    public List<Delivered> all() {
        return List.copyOf(delivered);
    }

    /** The most recent token for a purpose — what a person would click in their inbox. */
    public Optional<String> latest(TokenPurpose purpose) {
        return delivered.stream()
                .filter(entry -> entry.purpose() == purpose)
                .map(Delivered::rawToken)
                .reduce((first, second) -> second);
    }

    public void clear() {
        delivered.clear();
    }

    public record Delivered(
            EmailAddress recipient, TokenPurpose purpose, String rawToken, Instant expiresAt) {}
}
