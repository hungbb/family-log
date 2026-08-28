package app.giaky.lineage.domain;

import app.giaky.common.types.ClanId;
import app.giaky.common.types.PartialDate;
import app.giaky.common.types.PersonId;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * The tree read path, as three flat scans (B5.2, doc 03 §4).
 *
 * <p><b>A read model, not the aggregates.</b> {@link PersonRepository#findAllByClan} answers with
 * {@link Person} — the whole aggregate, including {@code notesHtml} and {@code notesText}, which for
 * a five-thousand-person clan is several megabytes of rich text nobody is going to render on a node
 * card. It also hydrates every row into the persistence context, where Hibernate keeps a snapshot of
 * it for dirty checking that a read-only request will never use. Both costs scale with the clan, and
 * both are paid on the hottest read in the product.
 *
 * <p>So the tree reads its own columns. Rows come back as records the payload can be built from
 * directly, and the three queries here are the three the design was sized against: persons by clan,
 * parentages by clan, unions by clan — each one indexed scan.
 *
 * <p>Rows are deliberately <i>not</i> {@link Person}, {@link Parentage} and {@link Union}. Those
 * carry invariants that belong to a write, and reconstructing them on a read path would mean
 * re-validating five thousand records to render a picture. What comes back here is what the wire
 * carries and nothing else.
 */
public interface TreeSnapshotRepository {

    /** Every live person, edge and union of one clan — the master view (FR-T2). */
    TreeSnapshot ofClan(ClanId clanId);

    /**
     * The same three lists, restricted to a set of people.
     *
     * <p>Edges and unions come back only when <b>both</b> of their endpoints are in {@code
     * personIds}: a payload that mentions a node it does not carry is a payload the client draws a
     * dangling line from, and a subtree read reaches exactly that case at its top.
     */
    TreeSnapshot ofPersons(ClanId clanId, Collection<PersonId> personIds);

    /** What one tree read returns. */
    record TreeSnapshot(List<NodeRow> persons, List<EdgeRow> parentages, List<UnionRow> unions) {

        public TreeSnapshot {
            persons = List.copyOf(persons);
            parentages = List.copyOf(parentages);
            unions = List.copyOf(unions);
        }

        public static TreeSnapshot empty() {
            return new TreeSnapshot(List.of(), List.of(), List.of());
        }
    }

    /**
     * A person, reduced to what a node card renders.
     *
     * @param branchId null for an unplaced node — the client badges it, and the resolver treats it
     *     as reachable only by a clan-wide or subtree grant
     * @param generation <i>đời</i>; null when unknown, never 0
     */
    record NodeRow(
            UUID id,
            UUID clanId,
            UUID branchId,
            String fullName,
            String alias,
            Gender gender,
            LifeStatus lifeStatus,
            PartialDate birth,
            PartialDate death,
            Integer generation,
            Integer birthOrder,
            UUID avatarMediaId,
            UUID linkedUserId,
            boolean foundingAncestor) {}

    /** One parent → child edge. */
    record EdgeRow(
            UUID id,
            UUID parentPersonId,
            UUID childPersonId,
            ParentRole parentRole,
            ParentageNature nature,
            UUID unionId) {}

    /** One marriage, partners in the canonical storage order the pair index depends on. */
    record UnionRow(
            UUID id,
            UUID partnerAId,
            UUID partnerBId,
            UnionType unionType,
            UnionStatus status,
            PartialDate startDate,
            PartialDate endDate,
            String notes) {}
}
