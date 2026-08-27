import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import {
  archiveBranch,
  createBranch,
  listBranches,
  moveBranch,
  updateBranch,
  type CreateBranchInput,
  type UpdateBranchInput,
} from '@/api/endpoints/branches.ts';

/**
 * Branch server state (doc 07 §5).
 *
 * Every mutation invalidates the whole tree rather than patching a node. A move rewrites the path
 * and depth of an entire subtree server-side, so a client that tried to apply the change locally
 * would be reimplementing the rule it just asked the server to apply — and would be wrong the first
 * time somebody moved a branch with children.
 */

export function branchesQueryKey(clanId: string) {
  return ['clan', clanId, 'branches'] as const;
}

export function useBranches(clanId: string) {
  return useQuery({
    queryKey: branchesQueryKey(clanId),
    queryFn: ({ signal }) => listBranches(clanId, signal),
    enabled: clanId !== '',
    staleTime: 30_000,
  });
}

export function useCreateBranch(clanId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (input: CreateBranchInput) => createBranch(clanId, input),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: branchesQueryKey(clanId) }),
  });
}

export function useUpdateBranch(clanId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (variables: { branchId: string; input: UpdateBranchInput }) =>
      updateBranch(clanId, variables.branchId, variables.input),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: branchesQueryKey(clanId) }),
  });
}

export function useMoveBranch(clanId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (variables: { branchId: string; newParentBranchId: string }) =>
      moveBranch(clanId, variables.branchId, variables.newParentBranchId),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: branchesQueryKey(clanId) }),
  });
}

export function useArchiveBranch(clanId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (branchId: string) => archiveBranch(clanId, branchId),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: branchesQueryKey(clanId) }),
  });
}
