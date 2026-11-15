package app.giaky.platform.infrastructure;

import app.giaky.common.types.TenantExempt;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data access to {@code mail_settings}. */
@TenantExempt(
        justification =
                "The mail transport belongs to the installation, not to a clan. There is one row"
                        + " and every clan's mail goes through it.")
public interface MailSettingsJpaRepository extends JpaRepository<MailSettingsJpaEntity, UUID> {}
