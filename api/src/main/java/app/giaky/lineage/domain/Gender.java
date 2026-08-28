package app.giaky.lineage.domain;

/**
 * A person's recorded gender.
 *
 * <p>{@link #UNKNOWN} is the default rather than a fallback. An old <i>gia phả</i> often lists a
 * child by birth order and nothing else, and a model whose default was {@code MALE} would quietly
 * assert something the book never said — in a document whose whole purpose is to be believed.
 *
 * <p>The product reads it for one thing beyond display: the kinship labels of F4.10, where
 * <i>cha</i> and <i>mẹ</i>, <i>vợ</i> and <i>chồng</i> differ. An unknown gender falls back to the
 * neutral term rather than guessing.
 */
public enum Gender {
    MALE,
    FEMALE,
    OTHER,
    UNKNOWN
}
