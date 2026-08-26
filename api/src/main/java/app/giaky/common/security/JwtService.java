package app.giaky.common.security;

import app.giaky.common.error.AuthenticationRequiredException;
import app.giaky.common.error.ErrorCode;
import app.giaky.common.types.UserId;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Mints and verifies access tokens.
 *
 * <p>HS256, 15 minutes, claims {@code sub / iat / exp / jti / email / tokenVersion} — doc 05 §5, and
 * nothing beyond it. Verification pins the algorithm through {@code verifyWith(SecretKey)}: jjwt
 * then refuses any token whose header names something else, which is what makes the classic
 * {@code alg: none} and "HMAC a token signed with the public key" attacks non-events here rather
 * than something this class has to remember to check.
 *
 * <p>{@code tokenVersion} travels in the token but is <i>not</i> trusted here — this class cannot
 * know the account's current value. {@code JwtAuthenticationFilter} compares the two; see
 * {@link AuthenticatedUserPort} for why that read exists.
 */
@Service
public class JwtService {

    private static final String CLAIM_EMAIL = "email";
    private static final String CLAIM_TOKEN_VERSION = "tokenVersion";

    private final JwtKeyProvider keyProvider;
    private final Clock clock;
    private final String issuer;
    private final Duration accessTokenTtl;

    public JwtService(JwtKeyProvider keyProvider, Clock clock, SecurityProperties properties) {
        this.keyProvider = keyProvider;
        this.clock = clock;
        this.issuer = properties.jwt().issuer();
        this.accessTokenTtl = properties.jwt().accessTokenTtl();
    }

    /** Issues a token for an account that has already been authenticated. */
    public AccessToken issue(UserId userId, String email, long tokenVersion) {
        Instant issuedAt = clock.instant();
        Instant expiresAt = issuedAt.plus(accessTokenTtl);

        String compact =
                Jwts.builder()
                        .issuer(issuer)
                        .subject(userId.value().toString())
                        .id(UUID.randomUUID().toString())
                        .issuedAt(Date.from(issuedAt))
                        .expiration(Date.from(expiresAt))
                        .claim(CLAIM_EMAIL, email)
                        .claim(CLAIM_TOKEN_VERSION, tokenVersion)
                        .signWith(keyProvider.signingKey(), Jwts.SIG.HS256)
                        .compact();

        return new AccessToken(compact, expiresAt, accessTokenTtl);
    }

    /**
     * Verifies a compact token and returns its claims.
     *
     * @throws AuthenticationRequiredException {@code TOKEN_EXPIRED} when it has simply aged out,
     *     {@code TOKEN_INVALID} for everything else — a bad signature, a tampered payload, an
     *     unexpected algorithm, a foreign issuer, a missing claim. The distinction matters because
     *     the client retries the first and gives up on the second (doc 07 §4.2).
     */
    public AccessTokenClaims verify(String compactToken) {
        Claims claims;
        try {
            claims =
                    Jwts.parser()
                            .verifyWith(keyProvider.signingKey())
                            .requireIssuer(issuer)
                            .clock(() -> Date.from(clock.instant()))
                            .build()
                            .parseSignedClaims(compactToken)
                            .getPayload();
        } catch (ExpiredJwtException expired) {
            throw new AuthenticationRequiredException(
                    ErrorCode.TOKEN_EXPIRED, "The access token has expired.");
        } catch (JwtException | IllegalArgumentException invalid) {
            // Includes an unsigned token, a wrong signature, a foreign issuer and unparseable input.
            // One answer for all of them: which one it was is information an attacker can use.
            throw new AuthenticationRequiredException(
                    ErrorCode.TOKEN_INVALID, "The access token is not valid.");
        }
        return toClaims(claims);
    }

    private static AccessTokenClaims toClaims(Claims claims) {
        try {
            return new AccessTokenClaims(
                    UserId.of(claims.getSubject()),
                    claims.get(CLAIM_EMAIL, String.class),
                    requireTokenVersion(claims),
                    claims.getId(),
                    claims.getExpiration().toInstant());
        } catch (IllegalArgumentException | NullPointerException malformed) {
            // Correctly signed but not one of ours: a claim is missing or the wrong type.
            throw new AuthenticationRequiredException(
                    ErrorCode.TOKEN_INVALID, "The access token is not valid.");
        }
    }

    private static long requireTokenVersion(Claims claims) {
        Number version = claims.get(CLAIM_TOKEN_VERSION, Number.class);
        if (version == null) {
            throw new IllegalArgumentException("tokenVersion claim is missing");
        }
        return version.longValue();
    }
}
