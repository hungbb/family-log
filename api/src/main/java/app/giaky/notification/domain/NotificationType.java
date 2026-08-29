package app.giaky.notification.domain;

/**
 * What a notification is about, as a closed vocabulary.
 *
 * <p>Stored as the constant's own name and rendered by the client through an i18n key built from it,
 * so these values are a contract in both directions: a rename orphans historical rows <i>and</i>
 * breaks a translation. Add freely, rename never — and doc 05 §6 requires the client to render an
 * unknown value rather than crash on it.
 */
public enum NotificationType {

    /** Somebody claimed a node you may decide claims for (FR-N2). */
    CLAIM_SUBMITTED,
    /** Your own claim was approved or rejected. */
    CLAIM_DECIDED,
    /** An editor linked your account to a node without you asking (FR-N4). */
    NODE_LINKED,
    /** Your link to a node was removed, by you or by an editor (FR-N5). */
    NODE_UNLINKED
}
