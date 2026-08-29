package app.giaky.linking.domain;

/**
 * Which account fields a member has agreed to have copied onto their node (FR-N6).
 *
 * <p><b>Both false by default</b>, in this record and in the table under it. Somebody filling in a
 * display name is editing their account, not the family book, and a product that quietly rewrote a
 * <i>gia phả</i> entry because of it would have done something nobody asked for. Opting in is a
 * separate, deliberate act.
 *
 * <p>Only two flags, because only two fields overlap: the account's photo and the name the person
 * goes by day to day. The latter lands on the node's {@code alias} — <i>tên thường gọi</i> — and
 * never on its {@code fullName}, which is what the book says and is nobody's to change from a
 * profile form.
 */
public record PropagationFlags(boolean avatar, boolean displayName) {

    /** What a node with no stored row has: nothing propagates. */
    public static PropagationFlags none() {
        return new PropagationFlags(false, false);
    }

    public boolean anyEnabled() {
        return avatar || displayName;
    }
}
