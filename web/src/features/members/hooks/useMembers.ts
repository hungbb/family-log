import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import {
  approveJoinRequest,
  cancelJoinRequest,
  listClanJoinRequests,
  listMyJoinRequests,
  rejectJoinRequest,
  type JoinRequestStatus,
} from '@/api/endpoints/joinRequests.ts';
import {
  leaveClan,
  listMembers,
  removeMember,
  type MemberFilters,
} from '@/api/endpoints/members.ts';
import { clansQueryKey } from '@/hooks/useClanMemberships.ts';

/** Members and join requests, as server state (doc 07 §5). */

export function membersQueryKey(clanId: string, filters: MemberFilters) {
  return ['clan', clanId, 'members', filters] as const;
}

export function joinRequestsQueryKey(clanId: string, status: JoinRequestStatus | undefined) {
  return ['clan', clanId, 'joinRequests', status ?? 'ALL'] as const;
}

export const myJoinRequestsQueryKey = ['me', 'joinRequests'] as const;

export function useMembers(clanId: string, filters: MemberFilters) {
  return useQuery({
    queryKey: membersQueryKey(clanId, filters),
    queryFn: ({ signal }) => listMembers(clanId, filters, signal),
    enabled: clanId !== '',
    staleTime: 30_000,
  });
}

/**
 * The approver's inbox.
 *
 * Refetched on window focus, because this is the screen somebody leaves open while they wait for a
 * relative to reply — and a stale inbox is one where two people approve the same request.
 */
export function useClanJoinRequests(clanId: string, status: JoinRequestStatus | undefined) {
  return useQuery({
    queryKey: joinRequestsQueryKey(clanId, status),
    queryFn: ({ signal }) => listClanJoinRequests(clanId, status, signal),
    enabled: clanId !== '',
    staleTime: 30_000,
    refetchOnWindowFocus: true,
  });
}

export function useMyJoinRequests() {
  return useQuery({
    queryKey: myJoinRequestsQueryKey,
    queryFn: ({ signal }) => listMyJoinRequests(signal),
    staleTime: 30_000,
    refetchOnWindowFocus: true,
  });
}

export function useApproveJoinRequest(clanId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (variables: { requestId: string; homeBranchId?: string }) =>
      approveJoinRequest(clanId, variables.requestId, variables.homeBranchId),
    onSuccess: () => {
      // An approval changes three things at once: the inbox, the member list, and — for the person
      // who was approved — the switcher. Only the first two are this browser's, so those are what
      // is invalidated here.
      void queryClient.invalidateQueries({ queryKey: ['clan', clanId] });
    },
  });
}

export function useRejectJoinRequest(clanId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (variables: { requestId: string; reason: string }) =>
      rejectJoinRequest(clanId, variables.requestId, variables.reason),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['clan', clanId] });
    },
  });
}

export function useCancelJoinRequest() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (requestId: string) => cancelJoinRequest(requestId),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: myJoinRequestsQueryKey });
    },
  });
}

export function useRemoveMember(clanId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (userId: string) => removeMember(clanId, userId),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['clan', clanId] });
    },
  });
}

export function useLeaveClan(clanId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => leaveClan(clanId),
    onSuccess: () => {
      queryClient.removeQueries({ queryKey: ['clan', clanId] });
      // The switcher has to lose the clan, or the shell keeps pointing at one that now 404s.
      void queryClient.invalidateQueries({ queryKey: clansQueryKey });
    },
  });
}
