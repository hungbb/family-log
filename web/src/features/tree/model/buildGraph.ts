import { type ParentageNature, type PersonSummary, type Union } from '@/api/endpoints/persons.ts';
import { inBookOrder, type Tree } from '@/api/endpoints/tree.ts';

/**
 * The flat payload, normalized into the shape a canvas can lay out (F5.1).
 *
 * **Pure, and deliberately so.** No React, no d3, no DOM. This is the piece of client logic most
 * worth testing when an automated frontend suite lands (doc 09 §4.3), and everything hard about the
 * tree happens here rather than in the renderer: which of two parents a child hangs from, how a man
 * with three wives becomes one row, what order siblings appear in.
 *
 * ## The three decisions this file makes
 *
 * **A couple is one layout slot.** A naive tree layout treats a marriage as two unrelated nodes and
 * draws the children under one of them, which is wrong in any book and unreadable in an old *gia
 * phả* where several wives are ordinary. So the unit of layout is an *anchor* — the person the line
 * of descent runs through — with their spouses beside them, and the children hang from the marriage
 * they were born into rather than from the anchor.
 *
 * **A child is drawn once.** A child with both parents in the payload is reachable from either of
 * them. The walk below claims each child the first time it is reached, in a deterministic order, so
 * "father's line then mother's line" produces the same tree every time and never draws a person
 * twice.
 *
 * **One person, one place.** A tree is a spanning tree of a graph that is not one, so somebody has
 * to be drawn once and only once. Two blood descendants of the same clan who marry each other — not
 * rare in an old book — are drawn as one couple under whichever of them the walk reaches first, and
 * the second one's edge to their own parents is not drawn. Nobody disappears and no child is
 * orphaned (their children hang from the couple), but that one connector is the price of a picture,
 * and the list view still shows both parentages. Any layout that avoided this would have to draw a
 * person twice, which is worse.
 *
 * **Married-in spouses are not roots.** The server's `rootPersonIds` is everybody with no parent in
 * the payload, which correctly includes every wife who married in. Those belong beside their
 * husband, not at the top of the page, so they are filtered out of the layout roots and re-enter as
 * part of his slot.
 */

/** One marriage inside a person's slot: the spouse, the union record, and the children of it. */
export interface Family {
  /** Stable across re-renders: the union's id, or the anchor's for children with one parent. */
  key: string;
  union?: Union;
  spouse?: PersonSummary;
  /** Ordered as the book writes them — see {@link inBookOrder}. */
  childIds: string[];
}

/**
 * One layout slot: a person, the spouses beside them, and the slots below.
 *
 * `children` is the flattened, ordered descent — every child of every family, in family order —
 * because that is what a tree layout consumes. Which family a child came from survives on the child
 * itself, in {@link CoupleNode.viaFamilyKey}, so the connector can start at the right union.
 */
export interface CoupleNode {
  key: string;
  anchor: PersonSummary;
  families: Family[];
  children: CoupleNode[];
  /** The family of the *parent* slot this node hangs from; undefined at a root. */
  viaFamilyKey?: string;
  /** How this node's edge into its parent was recorded — adopted children are drawn differently. */
  nature?: ParentageNature;
}

/** Everything a canvas, a toolbar and a minimap need, indexed once. */
export interface TreeGraph {
  roots: CoupleNode[];
  /** Every person in the payload, anchors and spouses alike. */
  personsById: Map<string, PersonSummary>;
  /** Layout slot by anchor id — what a "find me" or a search-and-jump resolves to. */
  nodesByKey: Map<string, CoupleNode>;
  /** Anchor id for any person, so clicking a wife focuses the couple she is part of. */
  slotOfPerson: Map<string, string>;
  /** Branch id → name, for badges and highlighting. */
  branchNames: Map<string, string>;
  /** People in the payload that no root reaches: an imported fragment, or a bad edge. */
  detachedIds: string[];
}

/** Builds the whole index in one pass over the payload. */
export function buildGraph(tree: Tree): TreeGraph {
  const personsById = new Map(tree.persons.map((person) => [person.id, person]));
  const unionsById = new Map(tree.unions.map((union) => [union.id, union]));

  // --- edges, both directions -----------------------------------------------------------------

  const parentIdsOf = new Map<string, string[]>();
  const childEdges = new Map<
    string,
    { childId: string; unionId?: string; nature: ParentageNature }[]
  >();

  for (const edge of tree.parentages) {
    if (!personsById.has(edge.parentPersonId) || !personsById.has(edge.childPersonId)) continue;

    const parents = parentIdsOf.get(edge.childPersonId) ?? [];
    parents.push(edge.parentPersonId);
    parentIdsOf.set(edge.childPersonId, parents);

    const children = childEdges.get(edge.parentPersonId) ?? [];
    children.push({
      childId: edge.childPersonId,
      ...(edge.unionId === undefined ? {} : { unionId: edge.unionId }),
      nature: edge.nature,
    });
    childEdges.set(edge.parentPersonId, children);
  }

  const unionIdsOf = new Map<string, string[]>();
  for (const union of tree.unions) {
    if (!personsById.has(union.partnerAId) || !personsById.has(union.partnerBId)) continue;
    for (const partnerId of [union.partnerAId, union.partnerBId]) {
      const held = unionIdsOf.get(partnerId) ?? [];
      held.push(union.id);
      unionIdsOf.set(partnerId, held);
    }
  }

  // --- who anchors a slot ---------------------------------------------------------------------

  // A person married to somebody who has a parent in the payload is drawn beside them. The reverse
  // — a husband who married into his wife's clan — is the same rule read the other way, and the
  // comparison below is what keeps it from being a rule about gender.
  const marriedInto = new Set<string>();
  for (const [personId, unionIds] of unionIdsOf) {
    if ((parentIdsOf.get(personId) ?? []).length > 0) continue;
    for (const unionId of unionIds) {
      const union = unionsById.get(unionId);
      if (union === undefined) continue;
      const partnerId = union.partnerAId === personId ? union.partnerBId : union.partnerAId;
      if ((parentIdsOf.get(partnerId) ?? []).length > 0) {
        marriedInto.add(personId);
        break;
      }
    }
  }

  // --- the walk --------------------------------------------------------------------------------

  const claimed = new Set<string>();
  const nodesByKey = new Map<string, CoupleNode>();
  const slotOfPerson = new Map<string, string>();

  const build = (
    personId: string,
    viaFamilyKey: string | undefined,
    nature: ParentageNature | undefined,
  ): CoupleNode | null => {
    const anchor = personsById.get(personId);
    if (anchor === undefined || claimed.has(personId)) return null;
    claimed.add(personId);
    slotOfPerson.set(personId, personId);

    const families = familiesOf(personId, {
      personsById,
      unionsById,
      unionIdsOf,
      childEdges,
      claimed,
    });
    for (const family of families) {
      if (family.spouse !== undefined) {
        // A spouse is part of this slot, so nobody else may anchor them.
        claimed.add(family.spouse.id);
        slotOfPerson.set(family.spouse.id, personId);
      }
    }

    const node: CoupleNode = {
      key: personId,
      anchor,
      families,
      children: [],
      ...(viaFamilyKey === undefined ? {} : { viaFamilyKey }),
      ...(nature === undefined ? {} : { nature }),
    };
    nodesByKey.set(personId, node);

    for (const family of families) {
      for (const childId of family.childIds) {
        const childNature = natureOf(childEdges, personId, childId);
        const child = build(childId, family.key, childNature);
        if (child !== null) node.children.push(child);
      }
    }
    return node;
  };

  const roots = tree.rootPersonIds
    .filter((id) => !marriedInto.has(id))
    .map((id) => personsById.get(id))
    .filter((person): person is PersonSummary => person !== undefined)
    .sort(inBookOrder)
    .map((person) => build(person.id, undefined, undefined))
    .filter((node): node is CoupleNode => node !== null);

  // Anybody the roots never reached. A payload should not contain these — the server derives its
  // roots from the same edges — but a depth-bounded read or a half-imported fragment can, and a
  // person who is silently missing from the picture is worse than one shown on their own.
  const detached: CoupleNode[] = [];
  for (const person of [...personsById.values()].sort(inBookOrder)) {
    if (claimed.has(person.id)) continue;
    const node = build(person.id, undefined, undefined);
    if (node !== null) detached.push(node);
  }

  return {
    roots: [...roots, ...detached],
    personsById,
    nodesByKey,
    slotOfPerson,
    branchNames: new Map(tree.branches.map((branch) => [branch.id, branch.name])),
    detachedIds: detached.map((node) => node.key),
  };
}

/** How many people a slot draws side by side — the anchor plus every recorded spouse. */
export function slotWidthInCards(node: CoupleNode): number {
  return 1 + node.families.filter((family) => family.spouse !== undefined).length;
}

/** Every person drawn in a slot, anchor first, then spouses in the order the families are held. */
export function peopleInSlot(node: CoupleNode): PersonSummary[] {
  const spouses = node.families
    .map((family) => family.spouse)
    .filter((spouse): spouse is PersonSummary => spouse !== undefined);
  return [node.anchor, ...spouses];
}

// --- internals ---------------------------------------------------------------------------------

interface Index {
  personsById: Map<string, PersonSummary>;
  unionsById: Map<string, Union>;
  unionIdsOf: Map<string, string[]>;
  childEdges: Map<string, { childId: string; unionId?: string; nature: ParentageNature }[]>;
  claimed: Set<string>;
}

/**
 * The families of one anchor, in the order the book reads them.
 *
 * A `PRIMARY` union comes first — she is the *chính thất*, and a book that listed a *thứ thất*
 * above her would be recording something the family did not say. After that, by the date the
 * marriage began, which is the only other ordering an old record supplies.
 *
 * The last family has no spouse and holds the children whose other parent is not recorded. Those
 * are common in an old book and must still be drawn; they simply hang from the anchor alone.
 */
function familiesOf(anchorId: string, index: Index): Family[] {
  const unionIds = (index.unionIdsOf.get(anchorId) ?? [])
    .map((unionId) => index.unionsById.get(unionId))
    .filter((union): union is Union => union !== undefined)
    .filter((union) => {
      // A union whose other partner already anchors a slot of their own would draw that person
      // twice. Their children still reach the tree through the partner who does anchor them.
      const partnerId = union.partnerAId === anchorId ? union.partnerBId : union.partnerAId;
      return !index.claimed.has(partnerId);
    })
    .sort(byUnionOrder);

  const ownChildren = index.childEdges.get(anchorId) ?? [];
  const assigned = new Set<string>();
  const families: Family[] = [];

  for (const union of unionIds) {
    const partnerId = union.partnerAId === anchorId ? union.partnerBId : union.partnerAId;
    const spouse = index.personsById.get(partnerId);
    const spouseChildIds = new Set(
      (index.childEdges.get(partnerId) ?? []).map((edge) => edge.childId),
    );

    // Two ways a child belongs to this marriage, and a real book supplies either: the edge names
    // the union outright, or both parents are recorded and the second one is this spouse.
    const childIds = ownChildren
      .filter(
        (edge) =>
          !assigned.has(edge.childId) &&
          (edge.unionId === union.id || spouseChildIds.has(edge.childId)),
      )
      .map((edge) => edge.childId);
    for (const childId of childIds) assigned.add(childId);

    families.push({
      key: union.id,
      union,
      ...(spouse === undefined ? {} : { spouse }),
      childIds: inOrder(childIds, index.personsById),
    });
  }

  const unattached = ownChildren
    .filter((edge) => !assigned.has(edge.childId))
    .map((edge) => edge.childId);
  if (unattached.length > 0) {
    families.push({
      key: `${anchorId}:only-parent`,
      childIds: inOrder(unattached, index.personsById),
    });
  }

  return families;
}

/** *Chính thất* first, then by when the marriage began, then by id so the order never wobbles. */
function byUnionOrder(left: Union, right: Union): number {
  const rank = (union: Union) => (union.unionType === 'PRIMARY' ? 0 : 1);
  if (rank(left) !== rank(right)) return rank(left) - rank(right);
  const leftYear = left.startDate?.year ?? Number.MAX_SAFE_INTEGER;
  const rightYear = right.startDate?.year ?? Number.MAX_SAFE_INTEGER;
  if (leftYear !== rightYear) return leftYear - rightYear;
  return left.id.localeCompare(right.id);
}

function inOrder(ids: string[], personsById: Map<string, PersonSummary>): string[] {
  return ids
    .map((id) => personsById.get(id))
    .filter((person): person is PersonSummary => person !== undefined)
    .sort(inBookOrder)
    .map((person) => person.id);
}

function natureOf(
  childEdges: Map<string, { childId: string; unionId?: string; nature: ParentageNature }[]>,
  parentId: string,
  childId: string,
): ParentageNature | undefined {
  return (childEdges.get(parentId) ?? []).find((edge) => edge.childId === childId)?.nature;
}
