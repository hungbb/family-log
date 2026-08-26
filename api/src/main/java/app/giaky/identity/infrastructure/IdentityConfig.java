package app.giaky.identity.infrastructure;

import app.giaky.identity.domain.PasswordPolicy;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

/**
 * Wiring for the parts of Identity that need something from outside the domain.
 *
 * <p>Only one so far: {@link PasswordPolicy} takes its deny list as a plain {@code Set}, because the
 * domain may not read a file or know that Spring exists. Loading it is this layer's job, which is
 * also what lets the policy be unit-tested against three passwords instead of a resource.
 */
@Configuration
public class IdentityConfig {

    private static final Logger log = LoggerFactory.getLogger(IdentityConfig.class);

    private static final String DENY_LIST = "identity/common-passwords.txt";

    @Bean
    public PasswordPolicy passwordPolicy() {
        Set<String> denied = loadDenyList();
        log.info("Password deny list loaded: {} entries", denied.size());
        return new PasswordPolicy(denied);
    }

    /**
     * Reads the deny list, one password per line, {@code #} for comments.
     *
     * <p>A missing file is a startup failure rather than an empty list. Silently accepting
     * {@code password123} because a resource did not make it into the jar is exactly the kind of
     * security regression nobody notices until it matters.
     */
    private static Set<String> loadDenyList() {
        ClassPathResource resource = new ClassPathResource(DENY_LIST);
        try (InputStream stream = resource.getInputStream();
                BufferedReader reader =
                        new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {

            Set<String> denied = new HashSet<>();
            String line;
            while ((line = reader.readLine()) != null) {
                String entry = line.trim().toLowerCase(Locale.ROOT);
                if (!entry.isEmpty() && !entry.startsWith("#")) {
                    denied.add(entry);
                }
            }
            return Set.copyOf(denied);
        } catch (IOException unreadable) {
            throw new IllegalStateException(
                    "Could not read the common-password deny list at " + DENY_LIST, unreadable);
        }
    }
}
