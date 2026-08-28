package app.giaky.lineage.application;

import app.giaky.common.types.PartialDate;
import app.giaky.lineage.domain.UnionStatus;
import app.giaky.lineage.domain.UnionType;

/**
 * Correct a marriage record (doc 05 §4.8).
 *
 * <p>The partners are not editable. Changing who a union is between is not a correction, it is two
 * different marriages — and expressing it as one edit would slip past the two-endpoint permission
 * check that created it.
 */
public record UpdateUnionCommand(
        UnionType unionType,
        UnionStatus status,
        PartialDate startDate,
        PartialDate endDate,
        String notes) {}
