package app.giaky.platform.infrastructure;

import app.giaky.common.types.UserId;
import app.giaky.platform.domain.MailSecurity;
import app.giaky.platform.domain.MailSettings;
import app.giaky.platform.domain.MailSettingsRepository;
import org.springframework.stereotype.Repository;

/**
 * The JPA side of {@link MailSettingsRepository}.
 *
 * <p>Mapped by hand rather than by MapStruct: one row, a dozen columns, and the one column whose
 * handling matters (the ciphertext) is easier to audit written out than generated.
 */
@Repository
public class MailSettingsRepositoryAdapter implements MailSettingsRepository {

    private final MailSettingsJpaRepository repository;

    public MailSettingsRepositoryAdapter(MailSettingsJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public MailSettings load() {
        return toDomain(row());
    }

    @Override
    public MailSettings save(MailSettings settings) {
        MailSettingsJpaEntity entity = row();
        if (entity.getVersion() != settings.version()) {
            throw new org.springframework.orm.ObjectOptimisticLockingFailureException(
                    MailSettingsJpaEntity.class, MailSettingsJpaEntity.SINGLETON_ID);
        }
        entity.setEnabled(settings.enabled());
        entity.setHost(settings.host());
        entity.setPort(settings.port());
        entity.setUsername(settings.username());
        entity.setPasswordCiphertext(settings.passwordCiphertext());
        entity.setSecurity(settings.security().name());
        entity.setFromAddress(settings.fromAddress());
        entity.setFromName(settings.fromName());
        entity.setUpdatedAt(settings.updatedAt());
        entity.setUpdatedBy(settings.updatedBy() == null ? null : settings.updatedBy().value());
        return toDomain(repository.saveAndFlush(entity));
    }

    private MailSettingsJpaEntity row() {
        return repository
                .findById(MailSettingsJpaEntity.SINGLETON_ID)
                .orElseThrow(
                        () ->
                                new IllegalStateException(
                                        "The mail_settings row is missing; V16 inserts it."));
    }

    private static MailSettings toDomain(MailSettingsJpaEntity entity) {
        return new MailSettings(
                entity.isEnabled(),
                entity.getHost(),
                entity.getPort(),
                entity.getUsername(),
                entity.getPasswordCiphertext(),
                MailSecurity.valueOf(entity.getSecurity()),
                entity.getFromAddress(),
                entity.getFromName(),
                entity.getVersion(),
                entity.getUpdatedAt(),
                entity.getUpdatedBy() == null ? null : UserId.of(entity.getUpdatedBy()));
    }
}
