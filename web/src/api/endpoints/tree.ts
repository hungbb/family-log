import { api } from '../client';

import { type Parentage, type PersonSummary, type Union } from './persons';

/**
 * `/clans/{clanId}/tree` — doc 05 §4.9.
 *
 * The payload is flat and the client assembles it. A family graph has no single spanning tree — a
 * child has two parents — so any nested shape duplicates somebody, and the duplication grows with
 * every second marriage. Four lists indexed by id is smaller on the wire and is the only shape that
 * survives the data.
 */

export interface TreeBranch {
  id: string;
  name: string;
  /** Materialized ancestry, e.g. `/a1b2c3d4/9f8e7d6c/`. The list view sorts and indents by it. */
  path: string;
  depth: number;
  rootPersonId?: string;
}

export interface TreeMeta {
  personCount: number;
  maxGeneration?: number;
  /** A depth bound cut the subtree short — offer to load more rather than showing a dead end. */
  truncated: boolean;
  generatedAt: string;
}

export interface Tree {
  clanId: string;
  /** Everybody with no parent inside the payload. Usually several: a founder, and wives who married in. */
  rootPersonIds: string[];
  persons: PersonSummary[];
  parentages: Parentage[];
  unions: Union[];
  branches: TreeBranch[];
  meta: TreeMeta;
  /** The counter behind the response's ETag (B4.12). */
  treeVersion: number;
}

export interface TreeStats {
  personCount: number;
  livingCount: number;
  deceasedCount: number;
  unlinkedCount: number;
  maxGeneration?: number;
  /** One entry per đời. The entry with no `generation` counts the people whose đời is unknown. */
  generationCounts: { generation?: number; count: number }[];
  branchCounts: { branchId: string; count: number }[];
}

export interface AncestorStep {
  person: PersonSummary;
  /** Generations up from the person asked about; 0 is that person. */
  depth: number;
}

/**
 * A bounded read of the tree (B5.7).
 *
 * ## When to use it, and why the default is not to
 *
 * The default is the whole clan, and that is deliberate at every size NFR-1 speaks of. The master
 * view is three indexed scans that the server caches per tree version, so the second reader of an
 * unchanged tree pays nothing; splitting the same clan into subtree requests turns one cached
 * response into dozens of uncached ones, and the client then has to stitch them back together and
 * decide what to do when two of them disagree because somebody edited in between.
 *
 * What costs on a large clan is not the payload — it is having five thousand nodes in the document.
 * The canvas answers that with virtualization and collapse, which are free and reversible, rather
 * than by fetching less.
 *
 * ## Above about three thousand nodes
 *
 * Past that, a client on a slow connection should stop loading the whole clan on first paint:
 *
 * 1. Open on a subtree — `{ rootPersonId, depth: 4 }` from the founding ancestor, or from the
 *    caller's own node once Phase 6 links accounts to people.
 * 2. Watch `meta.truncated`. It is true when the depth bound cut the family short, and it is what
 *    the "load more" affordance hangs off — a family that appears to end is worse than one that
 *    says it continues.
 * 3. Expand by re-reading with the expanded node as `rootPersonId`, keyed separately in the query
 *    cache (`treeQueryKey` already includes the root and the depth), and let a lineage mutation
 *    invalidate all of them at once — which is what `useInvalidateLineage` does.
 *
 * Nothing above is on by default. This is the escape hatch and the note that says when to reach for
 * it, not the path every clan takes.
 */
export interface TreeQuery {
  rootPersonId?: string;
  /** Generations below the root. Ignored without a root. */
  depth?: number;
}

export function getTree(clanId: string, query: TreeQuery, signal?: AbortSignal): Promise<Tree> {
  return api.get<Tree>(`/clans/${clanId}/tree`, {
    query: { rootPersonId: query.rootPersonId, depth: query.depth },
    ...(signal ? { signal } : {}),
  });
}

export function getAncestors(
  clanId: string,
  personId: string,
  signal?: AbortSignal,
): Promise<AncestorStep[]> {
  return api.get<AncestorStep[]>(
    `/clans/${clanId}/tree/ancestors/${personId}`,
    signal ? { signal } : {},
  );
}

export function getTreeStats(clanId: string, signal?: AbortSignal): Promise<TreeStats> {
  return api.get<TreeStats>(`/clans/${clanId}/tree/stats`, signal ? { signal } : {});
}

/** One node of the assembled tree: a person, their spouses, and the children below them. */
export interface TreeNode {
  person: PersonSummary;
  spouses: PersonSummary[];
  children: TreeNode[];
}

/**
 * Turns the flat payload into the nesting a table or a canvas renders.
 *
 * Pure and framework-free on purpose (doc 09 §4.3): this is the piece of client logic most worth
 * testing once a frontend suite exists, and it should not need a DOM to be tested.
 *
 * Children are ordered the way the book is written — birth order, then birth date, then name — and a
 * node is visited once even when both of its parents are in the payload, which is what stops a
 * child appearing twice under a couple.
 */
export function buildTreeNodes(tree: Tree): TreeNode[] {
  const byId = new Map(tree.persons.map((person) => [person.id, person]));
  const childIdsOf = new Map<string, string[]>();
  const parentIdsOf = new Map<string, string[]>();

  for (const edge of tree.parentages) {
    if (!byId.has(edge.parentPersonId) || !byId.has(edge.childPersonId)) continue;
    const children = childIdsOf.get(edge.parentPersonId) ?? [];
    children.push(edge.childPersonId);
    childIdsOf.set(edge.parentPersonId, children);

    const parents = parentIdsOf.get(edge.childPersonId) ?? [];
    parents.push(edge.parentPersonId);
    parentIdsOf.set(edge.childPersonId, parents);
  }

  const spouseIdsOf = new Map<string, string[]>();
  for (const union of tree.unions) {
    for (const [self, other] of [
      [union.partnerAId, union.partnerBId],
      [union.partnerBId, union.partnerAId],
    ] as const) {
      if (!byId.has(self) || !byId.has(other)) continue;
      const spouses = spouseIdsOf.get(self) ?? [];
      spouses.push(other);
      spouseIdsOf.set(self, spouses);
    }
  }

  // Spouses are drawn beside the person they married, not as separate roots. Anybody with a parent
  // in the payload is likewise not a root.
  const spouseOfSomeoneWithParents = new Set<string>();
  for (const [self, spouses] of spouseIdsOf) {
    if ((parentIdsOf.get(self) ?? []).length > 0) continue;
    for (const spouse of spouses) {
      if ((parentIdsOf.get(spouse) ?? []).length > 0) {
        spouseOfSomeoneWithParents.add(self);
      }
    }
  }

  const visited = new Set<string>();

  const toNode = (personId: string): TreeNode | null => {
    const person = byId.get(personId);
    if (person === undefined || visited.has(personId)) return null;
    visited.add(personId);

    const spouses = (spouseIdsOf.get(personId) ?? [])
      .map((id) => byId.get(id))
      .filter((candidate): candidate is PersonSummary => candidate !== undefined);
    for (const spouse of spouses) {
      visited.add(spouse.id);
    }

    // A couple's children hang from either partner's edges; collecting both and de-duplicating is
    // what stops a child appearing once under the father and once under the mother.
    const childIds = new Set<string>(childIdsOf.get(personId) ?? []);
    for (const spouse of spouses) {
      for (const childId of childIdsOf.get(spouse.id) ?? []) {
        childIds.add(childId);
      }
    }

    const children = [...childIds]
      .map((id) => byId.get(id))
      .filter((candidate): candidate is PersonSummary => candidate !== undefined)
      .sort(inBookOrder)
      .map((child) => toNode(child.id))
      .filter((node): node is TreeNode => node !== null);

    return { person, spouses, children };
  };

  return tree.rootPersonIds
    .filter((id) => !spouseOfSomeoneWithParents.has(id))
    .map((id) => byId.get(id))
    .filter((person): person is PersonSummary => person !== undefined)
    .sort(inBookOrder)
    .map((person) => toNode(person.id))
    .filter((node): node is TreeNode => node !== null);
}

/** Birth order, then birth year, then name — the order a family book is written in. */
export function inBookOrder(left: PersonSummary, right: PersonSummary): number {
  const byOrder = nullsLast(left.birthOrder, right.birthOrder);
  if (byOrder !== 0) return byOrder;
  const byBirth = nullsLast(left.birth?.year, right.birth?.year);
  if (byBirth !== 0) return byBirth;
  return left.fullName.localeCompare(right.fullName, 'vi');
}

function nullsLast(left: number | undefined, right: number | undefined): number {
  if (left === right) return 0;
  if (left === undefined) return 1;
  if (right === undefined) return -1;
  return left - right;
}
