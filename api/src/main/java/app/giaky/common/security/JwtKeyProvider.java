package app.giaky.common.security;

import javax.crypto.SecretKey;

/**
 * Supplies the key access tokens are signed and verified with.
 *
 * <p>The indirection exists so that the move doc 05 §5 anticipates — HS256 with a shared secret in
 * v1, RS256 with a published JWKS later — is a new implementation of this interface and nothing
 * else. {@code JwtService} is the only caller, and it never learns where the key came from.
 */
public interface JwtKeyProvider {

    /** The key for both signing and verification. Symmetric in v1; asymmetric would split this. */
    SecretKey signingKey();
}
