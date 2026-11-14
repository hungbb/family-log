package app.giaky.lineage.domain;

/**
 * A person's recorded gender: male or female.
 *
 * <p>Required on every person. The product reads it beyond display for the kinship labels of
 * F4.10, where <i>cha</i> and <i>mẹ</i>, <i>vợ</i> and <i>chồng</i> differ, and to decide which
 * parent role a person takes when a child is attached under them.
 */
public enum Gender {
    MALE,
    FEMALE
}
