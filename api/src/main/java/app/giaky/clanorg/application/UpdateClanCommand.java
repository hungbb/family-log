package app.giaky.clanorg.application;

import java.util.UUID;

/**
 * Edit a clan (FR-C3).
 *
 * <p>A null field means "leave it alone" — what an absent field in a PATCH means. There is no
 * {@code clanCode}: it is immutable (I-C1), and the way to guarantee that is for the command not to
 * be able to say it.
 *
 * @param expectedVersion the version the client read; a mismatch is a {@code 409 VERSION_CONFLICT}
 */
public record UpdateClanCommand(
        String name,
        String description,
        String originPlace,
        UUID coverMediaId,
        long expectedVersion) {}
