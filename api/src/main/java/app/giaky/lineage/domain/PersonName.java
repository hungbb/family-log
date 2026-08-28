package app.giaky.lineage.domain;

/**
 * A person's name, in the parts a Vietnamese family book actually keeps (doc 02 §3).
 *
 * <p>{@code fullName} is the source of truth and the only required part. The others are optional and
 * search-indexed, and every one of them exists because the books carry it:
 *
 * <ul>
 *   <li><b>familyName / givenName</b> — split when known, but never <i>derived</i> by splitting
 *       {@code fullName}. "Nguyễn Văn An" is family + middle + given, "Tôn Nữ Thị Ninh" is a
 *       four-part compound surname, and a naive split gets the second wrong in a way nobody notices
 *       until it is printed on a memorial.
 *   <li><b>alias</b> — <i>tên tự</i> or <i>tên hiệu</i>, the name a person was actually called.
 *   <li><b>posthumousName</b> — <i>tên thụy</i>, the name used at the giỗ. Often the only name a
 *       descendant knows an ancestor by, which is why it is indexed for search alongside the rest.
 * </ul>
 *
 * @param fullName the display name; non-blank after trimming (I-P1)
 */
public record PersonName(
        String fullName, String familyName, String givenName, String alias, String posthumousName) {

    /** {@code persons.full_name} is {@code VARCHAR(200)}. */
    public static final int FULL_NAME_MAX_LENGTH = 200;

    /** Every other name part is {@code VARCHAR(100)}. */
    public static final int PART_MAX_LENGTH = 100;

    public PersonName {
        fullName = requireFullName(fullName);
        familyName = trimPart(familyName, "familyName");
        givenName = trimPart(givenName, "givenName");
        alias = trimPart(alias, "alias");
        posthumousName = trimPart(posthumousName, "posthumousName");
    }

    /** The common case: a name written the way the book writes it, with no parts broken out. */
    public static PersonName of(String fullName) {
        return new PersonName(fullName, null, null, null, null);
    }

    private static String requireFullName(String value) {
        if (value == null) {
            throw new IllegalArgumentException("fullName must not be null");
        }
        String trimmed = value.trim();
        // I-P1. Also ck_persons_full_name in V7 — stated twice on purpose, because a blank name
        // makes a node that cannot be found, cannot be described in an error, and cannot be
        // recognised in the tree.
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("fullName must not be blank");
        }
        if (trimmed.length() > FULL_NAME_MAX_LENGTH) {
            throw new IllegalArgumentException(
                    "fullName must be at most " + FULL_NAME_MAX_LENGTH + " characters");
        }
        return trimmed;
    }

    private static String trimPart(String value, String field) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.length() > PART_MAX_LENGTH) {
            throw new IllegalArgumentException(
                    field + " must be at most " + PART_MAX_LENGTH + " characters");
        }
        return trimmed;
    }
}
