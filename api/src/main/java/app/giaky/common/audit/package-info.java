/**
 * The audit trail: the {@link app.giaky.common.audit.AuditWriter} port and its append-only JPA
 * adapter.
 *
 * <p>Phase 1 calls the writer inline from the application service, in a transaction of its own so a
 * rolled-back action still leaves a record. Phase 3 adds the {@code authorizingGrantId} — <i>which
 * grant</i> allowed an action (FR-Z8) — at which point the writer moves onto after-commit domain
 * events, because by then the answer is only known once the decision has been made.
 *
 * <p><b>Rule:</b> the table is append-only. Nothing here updates or deletes a row, and the database
 * refuses to as well.
 */
package app.giaky.common.audit;
