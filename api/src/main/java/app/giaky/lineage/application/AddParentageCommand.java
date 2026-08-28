package app.giaky.lineage.application;

import app.giaky.common.types.PersonId;
import app.giaky.lineage.domain.ParentRole;
import app.giaky.lineage.domain.ParentageNature;
import java.util.UUID;

/**
 * Link a parent to a child (doc 05 §4.8).
 *
 * <p>Needs {@code TREE_EDIT} on <b>both</b> people (doc 04 §5.2). That rule is what stops a branch
 * editor reaching into another chi by linking one of their own nodes to one of its.
 *
 * @param unionId the marriage the child was born into, when the caller knows it; null otherwise
 */
public record AddParentageCommand(
        PersonId parentPersonId,
        PersonId childPersonId,
        ParentRole parentRole,
        ParentageNature nature,
        UUID unionId) {

    public AddParentageCommand {
        nature = nature == null ? ParentageNature.BIOLOGICAL : nature;
    }
}
