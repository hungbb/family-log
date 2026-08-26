package app.giaky.identity.support;

import app.giaky.common.audit.AuditAction;
import app.giaky.common.audit.AuditRecord;
import app.giaky.common.audit.AuditWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * An {@link AuditWriter} that keeps what it was given.
 *
 * <p>Auditing is a requirement (task B1.13), not a side effect, so the tests assert on it directly
 * rather than trusting that a call was made.
 */
public final class RecordingAuditWriter implements AuditWriter {

    private final List<AuditRecord> written = new ArrayList<>();

    @Override
    public void write(AuditRecord record) {
        written.add(record);
    }

    public List<AuditRecord> written() {
        return List.copyOf(written);
    }

    public List<AuditAction> actions() {
        return written.stream().map(AuditRecord::action).toList();
    }

    public boolean recorded(AuditAction action) {
        return actions().contains(action);
    }

    public void clear() {
        written.clear();
    }
}
