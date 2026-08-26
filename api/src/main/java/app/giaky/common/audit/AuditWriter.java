package app.giaky.common.audit;

/**
 * Appends to the audit trail.
 *
 * <p>A port rather than a service so the write can be swapped — a Phase 8 deployment may want it on
 * a different connection, or off the request thread entirely — without touching a call site.
 *
 * <p><b>The write survives the caller's rollback.</b> A failed login is the case that proves why: it
 * ends in an exception, the transaction it ran in is rolled back, and an entry written inside that
 * transaction would vanish along with the evidence. Implementations therefore commit
 * independently.
 */
public interface AuditWriter {

    /**
     * Records one entry. Never throws: an audit failure must not turn a successful action into a
     * failed one, so implementations log and swallow.
     */
    void write(AuditRecord record);
}
