package app.giaky.lineage.application;

import app.giaky.common.types.PartialDate;
import app.giaky.lineage.domain.Gender;
import app.giaky.lineage.domain.LifeStatus;
import app.giaky.lineage.domain.ParentRole;
import app.giaky.lineage.domain.ParentageNature;

/**
 * Add a person to the tree (FR-P1, doc 05 §4.7).
 *
 * @param gender required: male or female
 * @param placement where the node attaches, which decides both its branch and the permission check
 * @param parentRole for a child placement, which parent the anchor is; defaults to the anchor's own
 *     gender when the caller does not say
 * @param nature for a child placement — <i>con nuôi</i> is recorded, not flattened
 * @param foundingAncestor claims đời 1. At most one per clan (I-P6)
 * @param notesHtml sanitized before it is stored, never on the way out (B4.13)
 */
public record CreatePersonCommand(
        String fullName,
        String familyName,
        String givenName,
        String alias,
        String posthumousName,
        Gender gender,
        LifeStatus lifeStatus,
        PartialDate birth,
        PartialDate death,
        String burialPlace,
        Integer birthOrder,
        String notesHtml,
        boolean foundingAncestor,
        PersonPlacement placement,
        ParentRole parentRole,
        ParentageNature nature) {

    public CreatePersonCommand {
        placement = placement == null ? PersonPlacement.unplaced() : placement;
        nature = nature == null ? ParentageNature.BIOLOGICAL : nature;
    }
}
