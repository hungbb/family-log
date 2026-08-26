package app.giaky.identity.infrastructure;

import app.giaky.common.security.SecurityProperties;
import app.giaky.identity.domain.PasswordHash;
import app.giaky.identity.domain.PasswordHasher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * BCrypt at cost 12 (FR-A2).
 *
 * <p>Twelve is chosen to be slow: roughly a quarter-second per verification on current hardware,
 * which is imperceptible on a login and ruinous for an attacker working through a stolen table.
 * Raising it later costs nothing structurally — the cost is encoded in each stored hash, so old
 * hashes keep verifying and new ones get the new factor.
 *
 * <p>BCrypt silently ignores input past 72 bytes, which is why {@code PasswordPolicy} refuses
 * anything longer rather than accepting a password whose tail does nothing.
 */
@Component
public class BCryptPasswordHasher implements PasswordHasher {

    private static final Logger log = LoggerFactory.getLogger(BCryptPasswordHasher.class);

    private final BCryptPasswordEncoder encoder;

    public BCryptPasswordHasher(SecurityProperties properties) {
        this.encoder = new BCryptPasswordEncoder(properties.bcryptCost());
    }

    @Override
    public PasswordHash hash(String rawPassword) {
        return PasswordHash.of(encoder.encode(rawPassword));
    }

    @Override
    public boolean matches(String rawPassword, PasswordHash hash) {
        if (rawPassword == null || hash == null) {
            return false;
        }
        try {
            return encoder.matches(rawPassword, hash.value());
        } catch (IllegalArgumentException malformed) {
            // A stored value that is not a BCrypt hash at all. Never a valid credential, and worth
            // knowing about: it means something wrote to the column that should not have.
            log.warn("Stored password hash is not in BCrypt format");
            return false;
        }
    }
}
