package app.giaky.common.audit.infrastructure;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Insert-only in practice: the table's trigger rejects {@code UPDATE} and {@code DELETE}, so the
 * inherited mutators would fail at the database if anything ever called them.
 *
 * <p>Reads arrive in Phase 3 with the audit UI, and are clan-scoped when they do.
 */
public interface AuditEntryJpaRepository extends JpaRepository<AuditEntryJpaEntity, UUID> {}
