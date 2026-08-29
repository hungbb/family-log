package app.giaky.linking.infrastructure;

import app.giaky.common.types.ClanId;
import app.giaky.common.types.PersonId;
import app.giaky.common.types.UserId;
import app.giaky.lineage.application.PersonLinkGateway;
import app.giaky.linking.domain.LinkableNode;
import app.giaky.linking.domain.PersonLinkPort;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * {@link PersonLinkPort} over Lineage's published {@link PersonLinkGateway}.
 *
 * <p>The whole of the seam. Linking never imports {@code lineage.domain} ({@code
 * architecture.LayeringTest} rule 4); it asks this adapter, which asks the gateway, which owns the
 * two statements that write the column. Three small classes rather than one import, and the reason
 * is that the import would be the first of many.
 */
@Component
public class LineagePersonLinkAdapter implements PersonLinkPort {

    private final PersonLinkGateway lineage;

    public LineagePersonLinkAdapter(PersonLinkGateway lineage) {
        this.lineage = lineage;
    }

    @Override
    public Optional<LinkableNode> find(ClanId clanId, PersonId personId) {
        return lineage.node(clanId, personId).map(LineagePersonLinkAdapter::toNode);
    }

    @Override
    public Optional<LinkableNode> findLinkedTo(ClanId clanId, UserId userId) {
        return lineage.nodeLinkedTo(clanId, userId).map(LineagePersonLinkAdapter::toNode);
    }

    @Override
    public boolean linkIfUnlinked(
            ClanId clanId, PersonId personId, UserId userId, UserId actor, Instant now) {

        return lineage.linkIfUnlinked(clanId, personId, userId, actor, now);
    }

    @Override
    public boolean unlink(ClanId clanId, PersonId personId, UserId actor, Instant now) {
        return lineage.unlink(clanId, personId, actor, now);
    }

    private static LinkableNode toNode(PersonLinkGateway.NodeRef ref) {
        return new LinkableNode(
                ref.id(), ref.clanId(), ref.branchId(), ref.fullName(), ref.linkedUserId());
    }
}
