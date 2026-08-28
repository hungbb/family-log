package app.giaky.lineage.application;

import app.giaky.lineage.domain.Parentage;
import app.giaky.lineage.domain.ParentRole;
import app.giaky.lineage.domain.ParentageNature;
import app.giaky.lineage.domain.TreeSnapshotRepository.EdgeRow;
import java.util.UUID;

/**
 * One parent-child edge, as the tree payload carries it (doc 05 §4.9).
 *
 * <p>Flat, and repeated per edge rather than nested inside either endpoint: a nested shape would
 * have to pick a direction, and the client assembles the graph in both.
 */
public record ParentageView(
        UUID id,
        UUID parentPersonId,
        UUID childPersonId,
        ParentRole parentRole,
        ParentageNature nature,
        UUID unionId) {

    static ParentageView of(Parentage parentage) {
        return new ParentageView(
                parentage.id(),
                parentage.parentPersonId().value(),
                parentage.childPersonId().value(),
                parentage.parentRole(),
                parentage.nature(),
                parentage.unionId());
    }

    /** The same edge, from the flat tree read (B5.2). */
    static ParentageView of(EdgeRow row) {
        return new ParentageView(
                row.id(),
                row.parentPersonId(),
                row.childPersonId(),
                row.parentRole(),
                row.nature(),
                row.unionId());
    }
}
