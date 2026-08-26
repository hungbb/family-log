package app.giaky.identity.application;

/**
 * Where a request came from.
 *
 * <p>Recorded on the session so the profile page can show "Chrome, 14.2.9.7, two days ago", and on
 * the audit entry so a suspicious login has somewhere to point. Both values are supplied by the
 * caller and are therefore untrusted: the user agent is whatever the client claims, and the address
 * is only as good as the proxy configuration behind it.
 *
 * @param ipAddress the client address, already resolved by the servlet container
 * @param userAgent the {@code User-Agent} header, truncated to what the column holds
 */
public record ClientContext(String ipAddress, String userAgent) {

    /** {@code refresh_tokens.user_agent} is {@code VARCHAR(255)}. */
    public static final int USER_AGENT_MAX_LENGTH = 255;

    /** {@code refresh_tokens.ip_address} is {@code VARCHAR(45)} — long enough for IPv6. */
    public static final int IP_ADDRESS_MAX_LENGTH = 45;

    /** For call sites with no request behind them: scheduled work, tests, the dev seed runner. */
    public static final ClientContext UNKNOWN = new ClientContext(null, null);

    public ClientContext {
        ipAddress = truncate(ipAddress, IP_ADDRESS_MAX_LENGTH);
        userAgent = truncate(userAgent, USER_AGENT_MAX_LENGTH);
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }
}
