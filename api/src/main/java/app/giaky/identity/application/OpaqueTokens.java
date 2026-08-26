package app.giaky.identity.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Generation and hashing of the opaque tokens — refresh, email verification, password reset.
 *
 * <p>256 bits from {@link SecureRandom}, base64url-encoded without padding, so a token is 43 URL-
 * and header-safe characters. The entropy is the entire security of the scheme: these are bearer
 * credentials with no structure to verify, so guessing one must be infeasible.
 *
 * <p>Stored as a plain SHA-256 rather than a password hash, and that is correct here: BCrypt exists
 * to slow down guessing a low-entropy secret a person chose, and there is nothing to slow down
 * against 256 random bits. What matters is that the raw value never reaches the database, so a
 * leaked table cannot be replayed.
 */
public final class OpaqueTokens {

    /** 32 bytes = 256 bits, as specified for the refresh token in doc 05 §5. */
    private static final int TOKEN_BYTES = 32;

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();

    private OpaqueTokens() {}

    /** A fresh token. Return it to the caller once; keep only {@link #hash(String)}. */
    public static String newToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return ENCODER.encodeToString(bytes);
    }

    /** Lowercase hex SHA-256, 64 characters — what goes in {@code token_hash}. */
    public static String hash(String rawToken) {
        try {
            byte[] digest =
                    MessageDigest.getInstance("SHA-256")
                            .digest(rawToken.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException impossible) {
            // Every JVM is required to provide SHA-256.
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }
}
