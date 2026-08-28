package app.giaky.lineage.application;

import app.giaky.common.types.PartialDate;
import app.giaky.common.types.PersonId;
import app.giaky.lineage.domain.UnionStatus;
import app.giaky.lineage.domain.UnionType;

/**
 * Record a marriage (doc 05 §4.8).
 *
 * <p>The pair is unordered; {@code Union.create} puts it in canonical order so I-N2 is a unique
 * index rather than a check the application has to remember in both directions.
 *
 * @param force overrides I-N4's refusal to marry two people who are already ancestor and descendant.
 *     A guard against data-entry error, not a moral position — and overridable, because a book that
 *     records a cousin marriage several generations deep is a book we must be able to transcribe.
 *     Requires {@code CLAN_ADMIN}
 */
public record CreateUnionCommand(
        PersonId partnerAId,
        PersonId partnerBId,
        UnionType unionType,
        UnionStatus status,
        PartialDate startDate,
        PartialDate endDate,
        String notes,
        boolean force) {

    public CreateUnionCommand {
        unionType = unionType == null ? UnionType.PRIMARY : unionType;
        status = status == null ? UnionStatus.UNKNOWN : status;
    }
}
