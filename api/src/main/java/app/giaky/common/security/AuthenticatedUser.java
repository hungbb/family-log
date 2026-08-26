package app.giaky.common.security;

import app.giaky.common.types.UserId;
import java.util.Objects;

/**
 * The authenticated principal: who the caller is, and nothing else.
 *
 * <p>Deliberately thin. It carries no permissions and no clan — doc 05 §5 keeps those out of the
 * access token because they change far more often than a 15-minute token lives, and doc 04 puts the
 * authoritative decision in {@code PermissionResolver}. A principal that carried roles would be a
 * cache nobody could invalidate.
 *
 * @param id the account, and the only thing other contexts ever see of it
 * @param email the signed-in address, for logging and for the UI's account menu
 * @param tokenVersion the value the presented token was minted with, already checked against the
 *     account's current one
 */
public record AuthenticatedUser(UserId id, String email, long tokenVersion) {

    public AuthenticatedUser {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(email, "email must not be null");
    }
}
