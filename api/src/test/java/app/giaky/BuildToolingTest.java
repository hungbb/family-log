package app.giaky;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

/**
 * The repository's own tooling — the part a mistake in stops a fresh clone before any code runs.
 *
 * <p>Not an architecture rule and not a unit of the product, which is why it sits beside the
 * application class rather than in a context: what it protects is the README's "running it, in
 * about five minutes".
 */
class BuildToolingTest {

    /**
     * The mode lives in the git index, and nothing on Windows ever looks at it: NTFS has no
     * executable bit, {@code mvnw.cmd} is what runs there, and regenerating the wrapper on that
     * side commits {@code mvnw} as {@code 100644} without anybody noticing. On macOS and Linux that
     * is {@code permission denied} for the first command the README gives.
     */
    @Test
    @DisabledOnOs(value = OS.WINDOWS, disabledReason = "NTFS has no executable bit to check")
    @DisplayName("./mvnw is executable, so the README's first command works on a fresh clone")
    void wrapperIsExecutable() {
        // Surefire runs with the module directory as the working directory.
        Path wrapper = Path.of("mvnw");

        assertThat(wrapper).exists();
        assertThat(Files.isExecutable(wrapper))
                .as("api/mvnw must be committed as 100755: git update-index --chmod=+x api/mvnw")
                .isTrue();
    }
}
