package app.giaky.common.security;

import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;

/**
 * The v1 {@link JwtKeyProvider}: one HMAC key, read from configuration and held for the life of the
 * process.
 *
 * <p>The secret is expected base64-encoded and must decode to at least 32 bytes — HS256's own key
 * size. A shorter key is not "weaker signing", it is a signature an attacker can brute-force
 * offline from a single captured token, so the constructor refuses to start the application rather
 * than logging a warning nobody reads.
 *
 * <p>A value that is not valid base64 is treated as raw UTF-8 bytes instead, with the same length
 * floor. That accommodates a hand-written development secret without weakening anything: the
 * length check is what matters, not the encoding.
 */
@Component
public class HmacJwtKeyProvider implements JwtKeyProvider {

    /** HS256 signs with a 256-bit key; anything shorter is brute-forceable offline. */
    private static final int MINIMUM_KEY_BYTES = 32;

    private final SecretKey key;

    public HmacJwtKeyProvider(SecurityProperties properties) {
        byte[] material = decode(properties.jwt().secret());
        if (material.length < MINIMUM_KEY_BYTES) {
            throw new IllegalStateException(
                    "app.security.jwt.secret must decode to at least "
                            + MINIMUM_KEY_BYTES
                            + " bytes for HS256; got "
                            + material.length);
        }
        this.key = Keys.hmacShaKeyFor(material);
    }

    @Override
    public SecretKey signingKey() {
        return key;
    }

    private static byte[] decode(String secret) {
        try {
            return Base64.getDecoder().decode(secret);
        } catch (IllegalArgumentException notBase64) {
            return secret.getBytes(StandardCharsets.UTF_8);
        }
    }
}
