package app.giaky.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.giaky.common.error.AuthenticationRequiredException;
import app.giaky.common.error.ErrorCode;
import app.giaky.common.types.UserId;
import io.jsonwebtoken.Jwts;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Access-token issue and verify, including the attacks the format invites.
 *
 * <p>The forgery cases are the reason this file exists. A JWT is only as good as its verification,
 * and the two classic ways to get that wrong — accepting {@code alg: none}, and accepting a
 * signature from a different key — are both tested here against real tokens built by hand.
 */
class JwtServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private static final Duration TTL = Duration.ofMinutes(15);
    private static final String ISSUER = "giaky-test";
    private static final String SECRET =
            "dGVzdC1vbmx5LXNpZ25pbmcta2V5LWZvci1hdXRvbWF0ZWQtdGVzdHMtMDE=";

    private static final UserId USER = UserId.of(UUID.randomUUID());

    private final JwtService jwtService = serviceAt(NOW);

    private static JwtService serviceAt(Instant instant) {
        SecurityProperties properties =
                new SecurityProperties(
                        new SecurityProperties.Jwt(SECRET, ISSUER, TTL),
                        new SecurityProperties.RefreshToken(Duration.ofDays(30)),
                        new SecurityProperties.Verification(Duration.ofHours(24), Duration.ofHours(1)),
                        12);
        return new JwtService(
                new HmacJwtKeyProvider(properties),
                Clock.fixed(instant, ZoneOffset.UTC),
                properties);
    }

    @Test
    @DisplayName("issues a token carrying exactly the claims doc 05 §5 specifies")
    void issuesTheSpecifiedClaims() {
        AccessToken token = jwtService.issue(USER, "an@example.com", 7L);

        AccessTokenClaims claims = jwtService.verify(token.value());

        assertThat(claims.userId()).isEqualTo(USER);
        assertThat(claims.email()).isEqualTo("an@example.com");
        assertThat(claims.tokenVersion()).isEqualTo(7L);
        assertThat(claims.tokenId()).isNotBlank();
        assertThat(claims.expiresAt()).isEqualTo(NOW.plus(TTL));
        assertThat(token.expiresAt()).isEqualTo(NOW.plus(TTL));
        assertThat(token.ttl()).isEqualTo(TTL);
    }

    @Test
    @DisplayName("carries no clan and no permission claims")
    void carriesNoAuthorizationClaims() {
        // Doc 05 §5. Permissions change far more often than a 15-minute token lives, so a token
        // that cached them would be a cache nobody could invalidate. This asserts on the encoded
        // payload rather than on the parsed claims, so a stray claim cannot hide behind the reader.
        String payload = jwtService.issue(USER, "an@example.com", 0L).value().split("\\.")[1];
        String decoded = new String(Base64.getUrlDecoder().decode(payload), StandardCharsets.UTF_8);

        assertThat(decoded)
                .doesNotContain("clan")
                .doesNotContain("permission")
                .doesNotContain("scope")
                .doesNotContain("role");
    }

    @Test
    @DisplayName("two tokens for one account have different jti values")
    void tokensAreDistinct() {
        String first = jwtService.issue(USER, "an@example.com", 0L).value();
        String second = jwtService.issue(USER, "an@example.com", 0L).value();

        assertThat(jwtService.verify(first).tokenId())
                .isNotEqualTo(jwtService.verify(second).tokenId());
    }

    @Test
    @DisplayName("an expired token is TOKEN_EXPIRED, which is what drives the client's refresh")
    void expiredTokenIsDistinguishable() {
        String token = jwtService.issue(USER, "an@example.com", 0L).value();
        JwtService later = serviceAt(NOW.plus(TTL).plusSeconds(1));

        assertRejectedWith(ErrorCode.TOKEN_EXPIRED, () -> later.verify(token));
    }

    @Test
    @DisplayName("a token one second before expiry still verifies")
    void tokenIsValidUntilItExpires() {
        String token = jwtService.issue(USER, "an@example.com", 0L).value();
        JwtService justBefore = serviceAt(NOW.plus(TTL).minusSeconds(1));

        assertThat(justBefore.verify(token).userId()).isEqualTo(USER);
    }

    @Test
    @DisplayName("an altered payload is rejected")
    void tamperedPayloadIsRejected() {
        String[] parts = jwtService.issue(USER, "an@example.com", 0L).value().split("\\.");
        String forgedPayload =
                Base64.getUrlEncoder()
                        .withoutPadding()
                        .encodeToString(
                                ("{\"sub\":\""
                                                + UUID.randomUUID()
                                                + "\",\"email\":\"attacker@example.com\",\"tokenVersion\":0,"
                                                + "\"iss\":\""
                                                + ISSUER
                                                + "\",\"exp\":99999999999}")
                                        .getBytes(StandardCharsets.UTF_8));
        String forged = parts[0] + '.' + forgedPayload + '.' + parts[2];

        assertRejectedWith(ErrorCode.TOKEN_INVALID, () -> jwtService.verify(forged));
    }

    @Test
    @DisplayName("an unsigned token — alg: none — is rejected")
    void unsignedTokenIsRejected() {
        String unsigned =
                Jwts.builder()
                        .issuer(ISSUER)
                        .subject(USER.value().toString())
                        .claim("email", "an@example.com")
                        .claim("tokenVersion", 0L)
                        .expiration(Date.from(NOW.plus(TTL)))
                        .compact();

        // Sanity: this really is a token with no signature, not something malformed.
        assertThat(unsigned).endsWith(".");
        assertRejectedWith(ErrorCode.TOKEN_INVALID, () -> jwtService.verify(unsigned));
    }

    @Test
    @DisplayName("a token signed with a different key is rejected")
    void foreignSignatureIsRejected() {
        SecretKey otherKey =
                io.jsonwebtoken.security.Keys.hmacShaKeyFor(
                        "a-different-key-of-at-least-32-bytes-long".getBytes(StandardCharsets.UTF_8));
        String forged =
                Jwts.builder()
                        .issuer(ISSUER)
                        .subject(USER.value().toString())
                        .claim("email", "an@example.com")
                        .claim("tokenVersion", 0L)
                        .expiration(Date.from(NOW.plus(TTL)))
                        .signWith(otherKey, Jwts.SIG.HS256)
                        .compact();

        assertRejectedWith(ErrorCode.TOKEN_INVALID, () -> jwtService.verify(forged));
    }

    @Test
    @DisplayName("a token from another issuer is rejected")
    void foreignIssuerIsRejected() {
        SecurityProperties elsewhere =
                new SecurityProperties(
                        new SecurityProperties.Jwt(SECRET, "somebody-else", TTL),
                        new SecurityProperties.RefreshToken(Duration.ofDays(30)),
                        new SecurityProperties.Verification(Duration.ofHours(24), Duration.ofHours(1)),
                        12);
        String foreign =
                new JwtService(
                                new HmacJwtKeyProvider(elsewhere),
                                Clock.fixed(NOW, ZoneOffset.UTC),
                                elsewhere)
                        .issue(USER, "an@example.com", 0L)
                        .value();

        assertRejectedWith(ErrorCode.TOKEN_INVALID, () -> jwtService.verify(foreign));
    }

    @Test
    @DisplayName("a correctly signed token missing tokenVersion is rejected")
    void missingClaimIsRejected() {
        SecretKey key =
                io.jsonwebtoken.security.Keys.hmacShaKeyFor(Base64.getDecoder().decode(SECRET));
        String incomplete =
                Jwts.builder()
                        .issuer(ISSUER)
                        .subject(USER.value().toString())
                        .claim("email", "an@example.com")
                        .expiration(Date.from(NOW.plus(TTL)))
                        .signWith(key, Jwts.SIG.HS256)
                        .compact();

        assertRejectedWith(ErrorCode.TOKEN_INVALID, () -> jwtService.verify(incomplete));
    }

    @Test
    @DisplayName("garbage is rejected as an invalid token, not as a crash")
    void garbageIsRejected() {
        assertRejectedWith(ErrorCode.TOKEN_INVALID, () -> jwtService.verify("not-a-token"));
        assertRejectedWith(ErrorCode.TOKEN_INVALID, () -> jwtService.verify(""));
    }

    @Test
    @DisplayName("a key shorter than HS256 requires stops the application, rather than signing weakly")
    void shortKeyIsRefusedAtStartup() {
        SecurityProperties weak =
                new SecurityProperties(
                        new SecurityProperties.Jwt("c2hvcnQ=", ISSUER, TTL),
                        new SecurityProperties.RefreshToken(Duration.ofDays(30)),
                        new SecurityProperties.Verification(Duration.ofHours(24), Duration.ofHours(1)),
                        12);

        assertThatThrownBy(() -> new HmacJwtKeyProvider(weak))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 32 bytes");
    }

    @Test
    @DisplayName("a secret that is not base64 is taken as raw bytes, so a hand-written one works")
    void nonBase64SecretIsUsedAsRawBytes() {
        SecurityProperties raw =
                new SecurityProperties(
                        new SecurityProperties.Jwt(
                                "a-hand-written-development-secret-that-is-long-enough", ISSUER, TTL),
                        new SecurityProperties.RefreshToken(Duration.ofDays(30)),
                        new SecurityProperties.Verification(Duration.ofHours(24), Duration.ofHours(1)),
                        12);

        assertThat(new HmacJwtKeyProvider(raw).signingKey()).isNotNull();
    }

    private static void assertRejectedWith(ErrorCode expected, ThrowingCallable call) {
        assertThatThrownBy(call)
                .isInstanceOf(AuthenticationRequiredException.class)
                .satisfies(
                        thrown ->
                                assertThat(((AuthenticationRequiredException) thrown).code())
                                        .isEqualTo(expected));
    }
}
