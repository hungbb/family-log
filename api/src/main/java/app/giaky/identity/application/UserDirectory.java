package app.giaky.identity.application;

import app.giaky.common.types.UserId;
import app.giaky.identity.domain.UserRepository;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Identity's published window onto accounts, for the contexts that hold a {@link UserId} and need a
 * name to show next to it.
 *
 * <p>This exists so that nothing else has to reach into {@code identity.domain} or join {@code
 * users} in its own SQL (doc 02 §7 rule 1). A member list has to render display names; without a
 * service like this, ClanOrg would either duplicate the column or write the join, and the boundary
 * would be gone by the second feature that wanted it.
 *
 * <p>It answers in {@link UserProfile}s, which cannot express a password hash or a token version —
 * the same reason {@code AccountService} does (I-U3).
 */
@Service
public class UserDirectory {

    /**
     * The cap on a name search.
     *
     * <p>The caller intersects the result with a clan's memberships, so a wide search costs the
     * intersection, not the response. A hundred is far more than any clan has members, and it stops
     * a one-character query from reading the whole account table.
     */
    public static final int MAX_SEARCH_RESULTS = 200;

    private final UserRepository users;

    public UserDirectory(UserRepository users) {
        this.users = users;
    }

    /**
     * Profiles for a set of accounts, in one query.
     *
     * @return the profiles by id; ids with no account are simply absent, because a deleted or
     *     unknown account is a gap in a list rather than a failure of the request
     */
    @Transactional(readOnly = true)
    public Map<UserId, UserProfile> profiles(Collection<UserId> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return users.findAllById(ids).stream()
                .map(UserProfile::of)
                .collect(
                        Collectors.toMap(
                                UserProfile::id,
                                Function.identity(),
                                (first, second) -> first,
                                LinkedHashMap::new));
    }

    /**
     * Accounts whose display name or address contains {@code query}.
     *
     * <p>Deliberately not diacritic-insensitive yet: doc 05 §4.7 asks for that on the person search,
     * which gets a {@code tsvector} column in Phase 4. Matching it here would mean the same
     * treatment on {@code users.display_name}, which is a migration this phase does not need.
     *
     * @return matching ids, capped at {@link #MAX_SEARCH_RESULTS}; empty for a blank query
     */
    @Transactional(readOnly = true)
    public List<UserId> search(String query) {
        String trimmed = query == null ? "" : query.trim();
        if (trimmed.isEmpty()) {
            return List.of();
        }
        return users.searchIds(trimmed, MAX_SEARCH_RESULTS);
    }

    /** The profile of one account, when the caller already knows it exists. */
    @Transactional(readOnly = true)
    public Optional<UserProfile> profile(UserId id) {
        return users.findById(id).map(UserProfile::of);
    }
}
