package app.giaky.common.audit.infrastructure;

import app.giaky.common.audit.AuditRecord;
import app.giaky.common.audit.AuditWriter;
import app.giaky.common.types.IdGenerator;
import app.giaky.common.web.CorrelationId;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Appends audit rows in a transaction of their own.
 *
 * <p>The independent transaction is the whole point. A failed login throws, the caller's
 * transaction rolls back, and an entry enlisted in it would roll back too — losing precisely the
 * record of the failure. {@code REQUIRES_NEW} keeps the evidence whatever the outcome.
 *
 * <p>It is also why this uses a {@link TransactionTemplate} rather than {@code @Transactional}: the
 * annotation belongs to application services and nowhere else (doc 09 §2.2), and the architecture
 * test enforces that. Doing it programmatically states the propagation at the one place it applies
 * instead of borrowing an annotation this layer is not entitled to.
 */
@Component
public class JpaAuditWriter implements AuditWriter {

    private static final Logger log = LoggerFactory.getLogger(JpaAuditWriter.class);

    private final AuditEntryJpaRepository repository;
    private final IdGenerator idGenerator;
    private final Clock clock;
    private final TransactionTemplate independentTransaction;

    public JpaAuditWriter(
            AuditEntryJpaRepository repository,
            IdGenerator idGenerator,
            Clock clock,
            PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.idGenerator = idGenerator;
        this.clock = clock;
        this.independentTransaction = new TransactionTemplate(transactionManager);
        this.independentTransaction.setPropagationBehavior(
                TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    public void write(AuditRecord record) {
        try {
            independentTransaction.executeWithoutResult(status -> repository.save(toEntity(record)));
        } catch (RuntimeException failure) {
            // An audit failure must never turn a successful action into a failed one. It is still a
            // real problem — a trail with holes is worth less than no trail — so it is logged loudly
            // with the correlation id that ties it to the request it lost.
            log.error("Failed to write audit entry {}", record.action(), failure);
        }
    }

    private AuditEntryJpaEntity toEntity(AuditRecord record) {
        return new AuditEntryJpaEntity(
                idGenerator.newId(),
                record.clanId() == null ? null : record.clanId().value(),
                record.actorUserId() == null ? null : record.actorUserId().value(),
                record.action().name(),
                record.targetType(),
                record.targetId(),
                record.beforeJson(),
                record.afterJson(),
                record.ipAddress(),
                CorrelationId.current(),
                clock.instant());
    }
}
