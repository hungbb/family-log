import { api } from '../client';

/**
 * `/clans/{clanId}/branches` — doc 05 §4.4.
 *
 * The tree endpoint returns branches already nested, so the AntD `Tree` on the other side is a
 * direct render rather than an assembly step.
 */

export interface Branch {
  id: string;
  clanId: string;
  parentBranchId?: string;
  /** Shareable code. Branch-scoped joining arrives with the permission model. */
  branchCode: string;
  name: string;
  description?: string;
  /** Materialized ancestry, e.g. `/a1b2c3d4/9f8e7d6c/`. */
  path: string;
  depth: number;
  archived: boolean;
  archivedAt?: string;
  /** Active members whose home branch is this one — not a subtree total. */
  memberCount: number;
  version: number;
  createdAt: string;
  updatedAt: string;
  children: Branch[];
}

export interface CreateBranchInput {
  /** Omit for directly under the clan's root branch. */
  parentBranchId?: string;
  name: string;
  description?: string;
}

export interface UpdateBranchInput {
  name?: string;
  description?: string;
  version: number;
}

export function listBranches(clanId: string, signal?: AbortSignal): Promise<Branch[]> {
  return api.get<Branch[]>(`/clans/${clanId}/branches`, signal ? { signal } : {});
}

export function createBranch(clanId: string, input: CreateBranchInput): Promise<Branch> {
  return api.post<Branch>(`/clans/${clanId}/branches`, input);
}

export function updateBranch(
  clanId: string,
  branchId: string,
  input: UpdateBranchInput,
): Promise<Branch> {
  return api.patch<Branch>(`/clans/${clanId}/branches/${branchId}`, input);
}

/**
 * Re-parents a branch, taking its whole subtree with it.
 *
 * The server refuses a move onto the branch's own descendant with `422 CYCLE_DETECTED`; the tree UI
 * disables those drop targets, but the refusal is the boundary and the UI is the convenience.
 */
export function moveBranch(
  clanId: string,
  branchId: string,
  newParentBranchId: string,
): Promise<Branch> {
  return api.post<Branch>(`/clans/${clanId}/branches/${branchId}/move`, { newParentBranchId });
}

export function archiveBranch(clanId: string, branchId: string): Promise<Branch> {
  return api.post<Branch>(`/clans/${clanId}/branches/${branchId}/archive`);
}

/** Flattens the nested tree, parents before children — for pickers and lookup maps. */
export function flattenBranches(branches: Branch[]): Branch[] {
  return branches.flatMap((branch) => [branch, ...flattenBranches(branch.children)]);
}

/**
 * Every branch's name by id, however deep it sits.
 *
 * The list endpoint answers with **one** top-level entry — the clan's root branch — and everything
 * else nested under `children`. A lookup built from the top level alone therefore knows the root
 * and nothing else: everybody placed in a chi came out as "in no branch", which is what the person
 * drawer and the search results said until they were given this.
 */
export function branchNamesById(branches: Branch[]): Map<string, string> {
  return new Map(flattenBranches(branches).map((branch) => [branch.id, branch.name]));
}

/**
 * The whole hierarchy as options for a flat select, parents before children.
 *
 * The indent makes the nesting legible without a tree component. Archived branches are left out
 * unless asked for: a picker that places something new should not offer a branch that is closed,
 * while a filter over existing records still has to reach the people who were placed there.
 */
export function branchSelectOptions(
  branches: Branch[],
  { includeArchived = false }: { includeArchived?: boolean } = {},
): { value: string; label: string }[] {
  return flattenBranches(branches)
    .filter((branch) => includeArchived || !branch.archived)
    .map((branch) => ({
      value: branch.id,
      label: `${'  '.repeat(branch.depth)}${branch.name}`,
    }));
}
