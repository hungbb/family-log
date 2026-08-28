import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { type ClanPermission } from '@/api/endpoints/clans.ts';
import {
  createGrant,
  getGrantable,
  getMyPermissions,
  listGrants,
  revokeGrant,
  type CreateGrantInput,
  type GrantFilters,
} from '@/api/endpoints/permissions.ts';

/**
 * Permission server state (doc 07 §5, F3.1).
 *
 * Keys follow `['clan', clanId, …]`, so every mutation that changes what somebody may do can
 * invalidate the whole clan subtree in one call — which is what a grant does: it changes the
 * permissions screen, the member drawer, and the menus the granter is looking at.
 */

export function myPermissionsQueryKey(clanId: string) {
  return ['clan', clanId, 'myPermissions'] as const;
}

export function grantsQueryKey(clanId: string, filters: GrantFilters) {
  return ['clan', clanId, 'grants', filters] as const;
}

export function grantableQueryKey(clanId: string) {
  return ['clan', clanId, 'grantable'] as const;
}

/**
 * What the caller holds in a clan.
 *
 * Five minutes of staleness (doc 07 §5). Permissions change rarely and are re-decided server-side
 * on every request, so a slightly old menu costs a 403 the UI already explains — whereas refetching
 * this on every navigation would put a request on every page load for an answer that almost never
 * moves.
 */
export function useMyPermissions(clanId: string | null) {
  return useQuery({
    queryKey: myPermissionsQueryKey(clanId ?? 'none'),
    queryFn: ({ signal }) => getMyPermissions(clanId ?? '', signal),
    enabled: clanId !== null && clanId !== '',
    staleTime: 5 * 60_000,
  });
}

/**
 * The gate the UI reads (F3.1).
 *
 * Returns a `can` that answers false while the query is still loading, which is the right default:
 * an affordance that flashes into existence and then disappears is worse than one that appears a
 * moment late. `isLoading` is exposed so a caller that would rather show a skeleton can.
 */
export function usePermissions(clanId: string | null): {
  permissions: ClanPermission[];
  can: (permission: ClanPermission) => boolean;
  isLoading: boolean;
} {
  const { data, isPending } = useMyPermissions(clanId);
  const permissions = data?.permissions ?? [];

  return {
    permissions,
    can: (permission) => permissions.includes(permission),
    isLoading: clanId !== null && clanId !== '' && isPending,
  };
}

/** Every live grant in the clan — the permissions table. Needs `ROLE_MANAGE`, so it 403s otherwise. */
export function useGrants(clanId: string, filters: GrantFilters = {}) {
  return useQuery({
    queryKey: grantsQueryKey(clanId, filters),
    queryFn: ({ signal }) => listGrants(clanId, filters, signal),
    enabled: clanId !== '',
    staleTime: 30_000,
  });
}

/**
 * What the caller may grant, and where (F3.4).
 *
 * Fetched when the dialog opens rather than with the page: it depends on the caller's own grants,
 * which the page they are on may have just changed.
 */
export function useGrantable(clanId: string, enabled: boolean) {
  return useQuery({
    queryKey: grantableQueryKey(clanId),
    queryFn: ({ signal }) => getGrantable(clanId, signal),
    enabled: enabled && clanId !== '',
    staleTime: 60_000,
  });
}

export function useCreateGrant(clanId: string) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (input: CreateGrantInput) => createGrant(clanId, input),
    onSuccess: () => {
      // Everything clan-scoped: the table, the grantable list, the member drawer, and — when
      // somebody grants themselves something through another route — their own menus.
      void queryClient.invalidateQueries({ queryKey: ['clan', clanId] });
    },
  });
}

export function useRevokeGrant(clanId: string) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (grantId: string) => revokeGrant(clanId, grantId),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['clan', clanId] });
    },
  });
}
