import { type QueryClient, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import {
  approveClaim,
  cancelClaim,
  listClaims,
  listMyClaims,
  rejectClaim,
  submitClaim,
  type ClaimStatus,
  type SubmitClaimInput,
} from '@/api/endpoints/claims.ts';
import {
  getMyLinkedNode,
  getPropagation,
  linkUser,
  listUnlinked,
  unlinkUser,
  updateSelfProfile,
  type SelfProfileInput,
  type UnlinkedFilters,
} from '@/api/endpoints/links.ts';

/**
 * Claims and links, as server state (doc 07 §5).
 *
 * Keys follow `['clan', clanId, …]`, so one invalidation covers everything a link changes — and a
 * link changes a lot at once: the tree (every node carries `linkedUserId`), the person drawer, the
 * candidate browser, the inbox, and the banner that asks somebody to find themselves.
 */

export function claimsQueryKey(clanId: string, status: ClaimStatus | undefined) {
  return ['clan', clanId, 'claims', status ?? 'ALL'] as const;
}

export function unlinkedQueryKey(clanId: string, filters: UnlinkedFilters) {
  return ['clan', clanId, 'unlinked', filters] as const;
}

export function myLinkedNodeQueryKey(clanId: string) {
  return ['clan', clanId, 'myLinkedNode'] as const;
}

export function propagationQueryKey(clanId: string, personId: string) {
  return ['clan', clanId, 'propagation', personId] as const;
}

export const myClaimsQueryKey = ['me', 'claims'] as const;

/**
 * The approver's inbox.
 *
 * Refetched on window focus, like the join-request inbox and for the same reason: this is a screen
 * somebody leaves open while they wait, and a stale one is where two approvers answer the same
 * claim — which the server then resolves with a 409 that reads like a bug.
 */
export function useClaims(clanId: string, status: ClaimStatus | undefined) {
  return useQuery({
    queryKey: claimsQueryKey(clanId, status),
    queryFn: ({ signal }) => listClaims(clanId, status, signal),
    enabled: clanId !== '',
    staleTime: 30_000,
    refetchOnWindowFocus: true,
  });
}

export function useMyClaims() {
  return useQuery({
    queryKey: myClaimsQueryKey,
    queryFn: ({ signal }) => listMyClaims(signal),
    staleTime: 30_000,
    refetchOnWindowFocus: true,
  });
}

/** The candidate list. Debouncing the search term is the caller's job, not the cache's. */
export function useUnlinkedCandidates(clanId: string, filters: UnlinkedFilters, enabled = true) {
  return useQuery({
    queryKey: unlinkedQueryKey(clanId, filters),
    queryFn: ({ signal }) => listUnlinked(clanId, filters, signal),
    enabled: enabled && clanId !== '',
    staleTime: 30_000,
  });
}

/**
 * The node the caller is recorded as, or null.
 *
 * Null is a real answer here, not an empty state to hide: it is what makes the "find yourself in
 * the tree" banner appear (F6.1).
 */
export function useMyLinkedNode(clanId: string | null) {
  return useQuery({
    queryKey: myLinkedNodeQueryKey(clanId ?? 'none'),
    queryFn: ({ signal }) => getMyLinkedNode(clanId ?? '', signal),
    enabled: clanId !== null && clanId !== '',
    staleTime: 60_000,
  });
}

export function usePropagationFlags(clanId: string, personId: string | null, enabled: boolean) {
  return useQuery({
    queryKey: propagationQueryKey(clanId, personId ?? 'none'),
    queryFn: ({ signal }) => getPropagation(clanId, personId ?? '', signal),
    enabled: enabled && clanId !== '' && personId !== null,
    staleTime: 60_000,
  });
}

export function useSubmitClaim(clanId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (input: SubmitClaimInput) => submitClaim(clanId, input),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['clan', clanId] });
      void queryClient.invalidateQueries({ queryKey: myClaimsQueryKey });
    },
  });
}

export function useCancelClaim(clanId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (claimId: string) => cancelClaim(clanId, claimId),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['clan', clanId] });
      void queryClient.invalidateQueries({ queryKey: myClaimsQueryKey });
    },
  });
}

export function useApproveClaim(clanId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (claimId: string) => approveClaim(clanId, claimId),
    onSuccess: () => {
      // An approval writes linked_user_id, which every tree node carries — so the tree, the drawer,
      // the candidate list and the inbox are all now wrong together.
      void queryClient.invalidateQueries({ queryKey: ['clan', clanId] });
      void queryClient.invalidateQueries({ queryKey: myClaimsQueryKey });
    },
  });
}

export function useRejectClaim(clanId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (variables: { claimId: string; reason: string }) =>
      rejectClaim(clanId, variables.claimId, variables.reason),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['clan', clanId] });
    },
  });
}

export function useLinkUser(clanId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (variables: { personId: string; userId: string }) =>
      linkUser(clanId, variables.personId, variables.userId),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['clan', clanId] });
    },
  });
}

/**
 * What an unlink makes stale, without asking for what the caller may no longer read.
 *
 * The self-service form is still mounted when the unlink succeeds; it unmounts only once the
 * refetched link says the node is no longer the caller's. Refetching its flags in that window asks
 * `GET …/self-profile` as somebody who is not the linked user any more, and the server rightly
 * answers 403. So the flags are marked stale without a refetch (a later link reads them fresh), and
 * everything else in the clan refetches as before. The same shape as `invalidateAfterRemoval` in
 * the tree feature.
 */
export function invalidateAfterUnlink(
  queryClient: QueryClient,
  clanId: string,
  personId: string,
): Promise<void> {
  const flagsKey = propagationQueryKey(clanId, personId);
  const flags = JSON.stringify(flagsKey);
  return Promise.all([
    queryClient.invalidateQueries({ queryKey: flagsKey, refetchType: 'none' }),
    queryClient.invalidateQueries({
      queryKey: ['clan', clanId],
      predicate: (query) => JSON.stringify(query.queryKey) !== flags,
    }),
  ]).then(() => undefined);
}

export function useUnlinkUser(clanId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (personId: string) => unlinkUser(clanId, personId),
    onSuccess: (_data, personId) => {
      void invalidateAfterUnlink(queryClient, clanId, personId);
    },
  });
}

export function useUpdateSelfProfile(clanId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (variables: { personId: string; input: SelfProfileInput }) =>
      updateSelfProfile(clanId, variables.personId, variables.input),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['clan', clanId] });
    },
  });
}
