package app.giaky.identity.application;

import app.giaky.common.audit.AuditAction;
import app.giaky.common.audit.AuditRecord;
import app.giaky.common.audit.AuditWriter;
import app.giaky.common.error.AuthenticationRequiredException;
import app.giaky.common.error.ErrorCode;
import app.giaky.common.security.SecurityProperties;
import app.giaky.common.types.IdGenerator;
import app.giaky.common.types.UserId;
import app.giaky.identity.domain.RefreshToken;
import app.giaky.identity.domain.RefreshToken.RevocationReason;
import app.giaky.identity.domain.RefreshTokenRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The refresh-token half of the session lifecycle: open, rotate, revoke (task B1.4).
 *
 * <h2>Rotation with reuse detection</h2>
 *
 * Every refresh spends the presented token and mints a successor in the same chain. A token is
 * therefore usable exactly once, and presenting one that has already been spent means two parties
 * hold the same credential — the client and whoever copied it. Which of the two is the thief cannot
 * be known, so the only safe answer is to end the session for both: {@link #rotate} revokes the
 * entire chain and the user has to sign in again.
 *
 * <p>The alternative — a long-lived refresh token that is not rotated — cannot detect theft at all.
 * A stolen token then works silently for thirty days.
 */
@Service
public class RefreshTokenService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);

    private final RefreshTokenRepository refreshTokens;
    private final AuditWriter auditWriter;
    private final IdGenerator idGenerator;
    private final Clock clock;
    private final Duration ttl;

    public RefreshTokenService(
            RefreshTokenRepository refreshTokens,
            AuditWriter auditWriter,
            IdGenerator idGenerator,
            Clock clock,
            SecurityProperties properties) {
        this.refreshTokens = refreshTokens;
        this.auditWriter = auditWriter;
        this.idGenerator = idGenerator;
        this.clock = clock;
        this.ttl = properties.refreshToken().ttl();
    }

    /** Opens a session: a new chain, and the raw token that starts it. */
    @Transactional
    public Issued open(UserId userId, ClientContext client) {
        Instant now = clock.instant();
        String raw = OpaqueTokens.newToken();

        RefreshToken stored =
                refreshTokens.save(
                        RefreshToken.open(
                                idGenerator.newId(),
                                userId,
                                OpaqueTokens.hash(raw),
                                now,
                                now.plus(ttl),
                                client.userAgent(),
                                client.ipAddress()));

        return new Issued(raw, stored);
    }

    /**
     * Exchanges a refresh token for its successor.
     *
     * @throws AuthenticationRequiredException {@code TOKEN_INVALID} for anything that is not a live
     *     token: unknown, expired, already rotated, revoked. One answer for all of them, because
     *     distinguishing "never existed" from "was yours yesterday" tells a thief which of their
     *     stolen tokens are worth trying.
     *     <p><b>The rejection does not roll this transaction back</b>, and must not. In the reuse
     *     case the method revokes the whole chain and <i>then</i> refuses: with the default rule the
     *     refusal undid the revocation it had just made, the log said "revoked 1 token(s)", the
     *     audit trail (which writes in its own transaction) said the same, and the thief's token
     *     went on working. {@code AuthService.refresh} wraps this call in its own transaction and
     *     carries the same rule for the same reason — one without the other is still a rollback.
     */
    @Transactional(noRollbackFor = AuthenticationRequiredException.class)
    public Issued rotate(String rawToken, ClientContext client) {
        Instant now = clock.instant();
        RefreshToken presented =
                refreshTokens
                        .findByTokenHash(OpaqueTokens.hash(rawToken))
                        .orElseThrow(RefreshTokenService::rejected);

        if (presented.isRevoked()) {
            // The reuse case. Whether this was a thief replaying a captured token or the real
            // client retrying after a lost response, the chain is compromised or confused; either
            // way it ends here.
            int revoked = refreshTokens.revokeChain(presented.chainId(), RevocationReason.REUSE_DETECTED, now);
            log.warn(
                    "Refresh token reuse detected for user {}; revoked {} token(s) in chain {}",
                    presented.userId(),
                    revoked,
                    presented.chainId());
            auditWriter.write(
                    AuditRecord.ofUser(AuditAction.REFRESH_TOKEN_REUSE_DETECTED, presented.userId())
                            .withIpAddress(client.ipAddress()));
            throw rejected();
        }
        if (presented.isExpiredAt(now)) {
            throw rejected();
        }

        refreshTokens.save(presented.revoke(RevocationReason.ROTATED, now));

        String raw = OpaqueTokens.newToken();
        RefreshToken successor =
                refreshTokens.save(
                        presented.successor(
                                idGenerator.newId(), OpaqueTokens.hash(raw), now, now.plus(ttl)));

        return new Issued(raw, successor);
    }

    /**
     * Revokes one session (FR-A5).
     *
     * <p>Silent about tokens it does not recognise. Signing out is not a place to learn whether a
     * token was ever valid, and a client whose token has already expired should still see its
     * sign-out succeed.
     */
    @Transactional
    public void revoke(String rawToken) {
        Optional<RefreshToken> found = refreshTokens.findByTokenHash(OpaqueTokens.hash(rawToken));
        found.ifPresent(token -> refreshTokens.save(token.revoke(RevocationReason.LOGOUT, clock.instant())));
    }

    /**
     * Revokes every session for an account (FR-A5).
     *
     * <p>Only half the job: this ends the refresh tokens, while outstanding <i>access</i> tokens stay
     * valid until they expire unless the account's {@code tokenVersion} is bumped too. {@code
     * AuthService} does both.
     *
     * @return how many sessions ended
     */
    @Transactional
    public int revokeAll(UserId userId, RevocationReason reason) {
        return refreshTokens.revokeAllForUser(userId, reason, clock.instant());
    }

    /** The account's live sessions, for the profile page. */
    @Transactional(readOnly = true)
    public List<SessionInfo> activeSessions(UserId userId) {
        return refreshTokens.findActiveByUser(userId, clock.instant()).stream()
                .map(SessionInfo::of)
                .toList();
    }

    private static AuthenticationRequiredException rejected() {
        return new AuthenticationRequiredException(
                ErrorCode.TOKEN_INVALID, "The refresh token is not valid. Sign in again.");
    }

    /**
     * A minted token: the raw value for the client, and the stored row for everything else.
     *
     * @param rawToken the only copy that exists outside the client
     * @param stored what the database holds — the hash, the chain, the expiry
     */
    public record Issued(String rawToken, RefreshToken stored) {}
}
