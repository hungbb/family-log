package app.giaky.lineage.domain;

import app.giaky.common.types.ClanId;

/**
 * The per-clan tree version behind the tree-read {@code ETag} (B4.12, doc 05 §4.9).
 *
 * <p>A counter rather than a hash of the payload. Hashing means producing the payload first, which
 * for a five-thousand-person clan is the expensive part of the request — the point of the tag is to
 * answer {@code 304} without doing that work. A counter costs one indexed read.
 *
 * <p>Its own row per clan, deliberately not {@code clans.version}: that column is the clan
 * aggregate's optimistic lock, and bumping it whenever somebody adds a cousin would answer a
 * concurrent settings edit with {@code 409 VERSION_CONFLICT} over a change that had nothing to do
 * with it (see V7).
 */
public interface TreeVersionRepository {

    /** The clan's current tree version. Zero for a clan whose tree has never been touched. */
    long current(ClanId clanId);

    /**
     * Increments and returns the new version. Called once per lineage mutation, inside its
     * transaction, so a reader either sees the old tree with the old tag or the new tree with the
     * new one — never a new tree behind a tag a client has already cached.
     */
    long bump(ClanId clanId);
}
