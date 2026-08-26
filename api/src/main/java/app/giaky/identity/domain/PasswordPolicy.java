package app.giaky.identity.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * What counts as an acceptable password (FR-A2, phase task B1.2).
 *
 * <p>Length and a deny list, and nothing else. The composition rules people expect — an uppercase, a
 * digit, a symbol — reliably produce {@code Password1!} and are not in the requirement; length plus
 * "not one of the passwords everybody uses" is what actually resists a guessing attack.
 *
 * <p>Returns its findings instead of throwing, because a registration form should be able to show
 * every problem at once rather than one per submission.
 *
 * <p>Framework-free by design: the deny list is handed in as a set, and loading it from a resource
 * file is the infrastructure layer's problem.
 */
public final class PasswordPolicy {

    /**
     * Eight, exactly as FR-A2 specifies.
     *
     * <p>The deny list is doing more work at this floor than it would at ten: every eight- and
     * nine-character password is now length-legal, and that range is where the passwords in every
     * breach corpus live — {@code password}, {@code 12345678}, {@code iloveyou}. Entries that short
     * were unreachable while the floor was higher; they are the point of the list now.
     */
    public static final int MIN_LENGTH = 8;

    /** BCrypt truncates beyond 72 <i>bytes</i>; refusing longer input is honest about that. */
    public static final int MAX_LENGTH = 72;

    private final Set<String> deniedPasswords;

    /**
     * @param deniedPasswords lowercase entries; compared case-insensitively, so {@code PASSWORD}
     *     and {@code password} are one entry
     */
    public PasswordPolicy(Set<String> deniedPasswords) {
        this.deniedPasswords = Set.copyOf(deniedPasswords);
    }

    /**
     * Every rule the candidate breaks, in the order a person would want to fix them.
     *
     * @return an empty list when the password is acceptable
     */
    public List<String> validate(String candidate) {
        List<String> violations = new ArrayList<>(2);
        if (candidate == null || candidate.isEmpty()) {
            violations.add("Password must not be empty.");
            return List.copyOf(violations);
        }
        if (candidate.length() < MIN_LENGTH) {
            violations.add("Password must be at least " + MIN_LENGTH + " characters.");
        }
        if (candidate.length() > MAX_LENGTH) {
            violations.add("Password must be at most " + MAX_LENGTH + " characters.");
        }
        if (deniedPasswords.contains(candidate.toLowerCase(Locale.ROOT))) {
            violations.add("This password is too common. Choose something less predictable.");
        }
        return List.copyOf(violations);
    }

    /** Convenience over {@link #validate}. */
    public boolean isAcceptable(String candidate) {
        return validate(candidate).isEmpty();
    }
}
