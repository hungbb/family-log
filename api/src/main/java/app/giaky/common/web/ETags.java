package app.giaky.common.web;

/**
 * Strong {@code ETag}s derived from a resource's version counter.
 *
 * <p>Used on the read paths that carry an explicit version — the clan tree is stamped with its
 * {@code treeVersion}, an aggregate with its optimistic-locking {@code version} — so a client can
 * revalidate with {@code If-None-Match} and get a {@code 304} instead of the payload.
 *
 * <p>Distinct from the {@code ShallowEtagHeaderFilter} registered in {@code WebConfig}: that one
 * hashes the rendered body and still pays the cost of producing it, this one short-circuits before
 * the work happens.
 */
public final class ETags {

    private ETags() {}

    /** Formats a version as a strong entity tag, e.g. {@code "v42"}. */
    public static String of(long version) {
        return "\"v" + version + "\"";
    }

    /**
     * Whether an {@code If-None-Match} header matches the current version.
     *
     * @param ifNoneMatch the raw header value, possibly {@code null} or a comma-separated list
     */
    public static boolean matches(String ifNoneMatch, long version) {
        if (ifNoneMatch == null || ifNoneMatch.isBlank()) {
            return false;
        }
        String current = of(version);
        for (String candidate : ifNoneMatch.split(",")) {
            String trimmed = candidate.trim();
            // A weak validator ("W/\"v42\"") still identifies the same representation here.
            if (trimmed.startsWith("W/")) {
                trimmed = trimmed.substring(2);
            }
            if ("*".equals(trimmed) || current.equals(trimmed)) {
                return true;
            }
        }
        return false;
    }
}
