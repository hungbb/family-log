import { api } from '../client';

import type { Page } from './members';

/**
 * Join requests — doc 05 §4.5 and §4.2.
 *
 * Two audiences in one module, because they are two sides of one thing: the requester submits,
 * watches and withdraws; the approver reads an inbox and answers.
 */

export type JoinRequestStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'CANCELLED' | 'EXPIRED';

export interface JoinRequest {
  id: string;
  clanId: string;
  clanName: string;
  clanCode: string;
  targetBranchId?: string;
  targetBranchName?: string;
  requesterUserId: string;
  requesterName?: string;
  requesterEmail?: string;
  message?: string;
  status: JoinRequestStatus;
  decidedBy?: string;
  decidedAt?: string;
  decisionReason?: string;
  createdAt: string;
  expiresAt: string;
}

/** Exactly one of the two codes. Both are the same shape, so the client sends whichever it has. */
export interface SubmitJoinRequestInput {
  clanCode?: string;
  branchCode?: string;
  message?: string;
}

export function submitJoinRequest(input: SubmitJoinRequestInput): Promise<JoinRequest> {
  return api.post<JoinRequest>('/join-requests', input);
}

export function cancelJoinRequest(requestId: string): Promise<void> {
  return api.del<undefined>(`/join-requests/${requestId}`);
}

/** The caller's own requests, across every clan — including ones they are not a member of. */
export function listMyJoinRequests(signal?: AbortSignal): Promise<JoinRequest[]> {
  return api.get<JoinRequest[]>('/me/join-requests', signal ? { signal } : {});
}

export function listClanJoinRequests(
  clanId: string,
  status: JoinRequestStatus | undefined,
  signal?: AbortSignal,
): Promise<Page<JoinRequest>> {
  return api.get<Page<JoinRequest>>(`/clans/${clanId}/join-requests`, {
    query: { status, size: 50 },
    ...(signal ? { signal } : {}),
  });
}

/** Omitting `homeBranchId` accepts the branch the requester asked for. */
export function approveJoinRequest(
  clanId: string,
  requestId: string,
  homeBranchId?: string,
): Promise<JoinRequest> {
  return api.post<JoinRequest>(`/clans/${clanId}/join-requests/${requestId}/approve`, {
    homeBranchId,
  });
}

export function rejectJoinRequest(
  clanId: string,
  requestId: string,
  reason: string,
): Promise<JoinRequest> {
  return api.post<JoinRequest>(`/clans/${clanId}/join-requests/${requestId}/reject`, { reason });
}
