/**
 * The audit trail: the {@code AuditWriter} port and its append-only JPA adapter.
 *
 * <p>Populated in a later phase. Audit entries record <i>which grant</i> authorised an action, so the
 * writer consumes domain events after commit rather than being called inline.
 */
package app.giaky.common.audit;
