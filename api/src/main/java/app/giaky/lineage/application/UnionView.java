package app.giaky.lineage.application;

import app.giaky.common.types.PartialDate;
import app.giaky.lineage.domain.TreeSnapshotRepository.UnionRow;
import app.giaky.lineage.domain.Union;
import app.giaky.lineage.domain.UnionStatus;
import app.giaky.lineage.domain.UnionType;
import java.util.UUID;

/**
 * One marriage, as the tree payload carries it (doc 05 §4.9).
 *
 * <p>{@code partnerAId} and {@code partnerBId} are in the canonical storage order, which carries no
 * meaning — the client decides who to draw first.
 */
public record UnionView(
        UUID id,
        UUID partnerAId,
        UUID partnerBId,
        UnionType unionType,
        UnionStatus status,
        PartialDate startDate,
        PartialDate endDate,
        String notes) {

    static UnionView of(Union union) {
        return new UnionView(
                union.id(),
                union.partnerAId().value(),
                union.partnerBId().value(),
                union.unionType(),
                union.status(),
                union.startDate(),
                union.endDate(),
                union.notes());
    }

    /** The same marriage, from the flat tree read (B5.2). */
    static UnionView of(UnionRow row) {
        return new UnionView(
                row.id(),
                row.partnerAId(),
                row.partnerBId(),
                row.unionType(),
                row.status(),
                row.startDate(),
                row.endDate(),
                row.notes());
    }
}
