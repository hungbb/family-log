package app.giaky.identity.application;

/**
 * @param email raw from the request; may be anything at all, including something that is not an
 *     address, which must produce the same answer as a wrong password
 * @param password raw
 * @param client recorded on the session and on the audit entry
 */
public record LoginCommand(String email, String password, ClientContext client) {}
