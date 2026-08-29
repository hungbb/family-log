import { api } from '../client';

import type { Page } from './members';

/**
 * Node claims — doc 05 §4.10 and §4.2.
 *
 * Two audiences in one module, as `joinRequests.ts` is: a member claims, watches and withdraws;
 * an approver reads an inbox and answers. Unlike a join request, both sides are clan-scoped —
 * somebody claiming a node is already a member of the clan the node is in.
 */

export type ClaimStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'CANCELLED';

export interface NodeClaim {
  id: string;
  clanId: string;
  /** Populated only on `/me/claims`, which spans clans. */
  clanName?: string;
  personId: string;
  personName?: string;
  /** đời. Null when unknown — never 0. */
  personGeneration?: number;
  /**
   * The node's recorded parents and children.
   *
   * The approver's whole verification tool (F6.5): two Nguyễn Văn Ans of the same generation are
   * told apart by whose son each of them was, not by a birth year the book rarely has.
   */
  parentNames: string[];
  childNames: string[];
  requesterUserId: string;
  requesterName?: string;
  /** Only an approver receives this. On the requester's own list it would be their own address. */
  requesterEmail?: string;
  evidence?: string;
  status: ClaimStatus;
  decidedBy?: string;
  decidedByName?: string;
  decidedAt?: string;
  decisionReason?: string;
  createdAt: string;
}

export interface SubmitClaimInput {
  personId: string;
  evidence?: string;
}

export function submitClaim(clanId: string, input: SubmitClaimInput): Promise<NodeClaim> {
  return api.post<NodeClaim>(`/clans/${clanId}/claims`, input);
}

export function cancelClaim(clanId: string, claimId: string): Promise<void> {
  return api.del<undefined>(`/clans/${clanId}/claims/${claimId}`);
}

/** The approver inbox, already filtered server-side to what the caller may decide. */
export function listClaims(
  clanId: string,
  status: ClaimStatus | undefined,
  signal?: AbortSignal,
): Promise<Page<NodeClaim>> {
  return api.get<Page<NodeClaim>>(`/clans/${clanId}/claims`, {
    query: { status, size: 50 },
    ...(signal ? { signal } : {}),
  });
}

export function approveClaim(clanId: string, claimId: string): Promise<NodeClaim> {
  return api.post<NodeClaim>(`/clans/${clanId}/claims/${claimId}/approve`);
}

export function rejectClaim(clanId: string, claimId: string, reason: string): Promise<NodeClaim> {
  return api.post<NodeClaim>(`/clans/${clanId}/claims/${claimId}/reject`, { reason });
}

/** The caller's own claims, across every clan — the list on `/me` (F6.4). */
export function listMyClaims(signal?: AbortSignal): Promise<NodeClaim[]> {
  return api.get<NodeClaim[]>('/me/claims', signal ? { signal } : {});
}
