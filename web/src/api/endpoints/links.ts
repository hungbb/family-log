import { api } from '../client';

import type { Page, PersonSummary } from './persons';

/**
 * The link between an account and a person node — doc 05 §4.10 (FR-N3…FR-N6).
 *
 * Separate from `persons.ts` even though the paths share a prefix, because they are separate
 * concerns on the server too: Lineage owns what the book says about somebody, Linking owns which
 * account is them.
 */

export interface LinkedNode {
  personId: string;
  personName?: string;
  clanId: string;
  /** Null when nobody is linked, which is most nodes. */
  linkedUserId?: string;
  linkedUserName?: string;
  /** Whether the caller *is* this node — what the tree renders as "this is you" (F6.7). */
  self: boolean;
}

/** One candidate in the unlinked-node browser (F6.2). */
export interface UnlinkedCandidate {
  person: PersonSummary;
  parentNames: string[];
  childNames: string[];
}

export interface UnlinkedFilters {
  q?: string;
  generation?: number;
  branchId?: string;
  page?: number;
  size?: number;
}

/**
 * The node the caller is recorded as in this clan.
 *
 * Resolves to null when they hold none — the server answers `204`, and the client wrapper turns an
 * empty body into `undefined`. That is the case the onboarding banner exists for (F6.1), so it is
 * not an error and must not be rendered as one.
 */
export async function getMyLinkedNode(
  clanId: string,
  signal?: AbortSignal,
): Promise<LinkedNode | null> {
  const found = await api.get<LinkedNode | undefined>(
    `/clans/${clanId}/persons/linked-to-me`,
    signal ? { signal } : {},
  );
  return found ?? null;
}

export function getLink(
  clanId: string,
  personId: string,
  signal?: AbortSignal,
): Promise<LinkedNode> {
  return api.get<LinkedNode>(
    `/clans/${clanId}/persons/${personId}/link-user`,
    signal ? { signal } : {},
  );
}

export function listUnlinked(
  clanId: string,
  filters: UnlinkedFilters,
  signal?: AbortSignal,
): Promise<Page<UnlinkedCandidate>> {
  return api.get<Page<UnlinkedCandidate>>(`/clans/${clanId}/persons/unlinked`, {
    query: {
      q: filters.q,
      generation: filters.generation,
      branchId: filters.branchId,
      page: filters.page,
      size: filters.size,
    },
    ...(signal ? { signal } : {}),
  });
}

/** Direct assignment (FR-N4). Needs `NODE_CLAIM_APPROVE` covering the node. */
export function linkUser(clanId: string, personId: string, userId: string): Promise<LinkedNode> {
  return api.post<LinkedNode>(`/clans/${clanId}/persons/${personId}/link-user`, { userId });
}

/** Unlink (FR-N5). Allowed to an approver in scope, and to the linked person themselves. */
export function unlinkUser(clanId: string, personId: string): Promise<void> {
  return api.del<undefined>(`/clans/${clanId}/persons/${personId}/link-user`);
}

/** Which account fields the caller has agreed to have copied onto their node (FR-N6). */
export interface PropagationFlags {
  propagateAvatar: boolean;
  propagateDisplayName: boolean;
}

export function getPropagation(
  clanId: string,
  personId: string,
  signal?: AbortSignal,
): Promise<PropagationFlags> {
  return api.get<PropagationFlags>(
    `/clans/${clanId}/persons/${personId}/self-profile`,
    signal ? { signal } : {},
  );
}

/**
 * The self-service edit (doc 04 §6).
 *
 * **This interface is the whitelist.** Two editable fields and no others; a name, a date or a
 * relationship still needs `TREE_EDIT` and still goes through `patchPerson`, which answers 403 to
 * somebody who only holds their own node.
 */
export interface SelfProfileInput {
  avatarMediaId?: string;
  notesHtml?: string;
  propagateAvatar: boolean;
  propagateDisplayName: boolean;
}

export function updateSelfProfile(
  clanId: string,
  personId: string,
  input: SelfProfileInput,
): Promise<LinkedNode> {
  return api.patch<LinkedNode>(`/clans/${clanId}/persons/${personId}/self-profile`, input);
}
