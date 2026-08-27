package app.giaky.clanorg.domain;

/**
 * Who can see a clan.
 *
 * <p>One value in v1, and the enum exists anyway: {@code PRIVATE} is a decision, and a boolean
 * column called {@code is_private} would make the second decision a migration and a rename rather
 * than a new constant.
 */
public enum ClanVisibility {

    /** Visible only to active members. A non-member gets a 404, never a 403 (doc 04 §7). */
    PRIVATE
}
