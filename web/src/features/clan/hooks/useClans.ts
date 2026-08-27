import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import {
  createClan,
  deleteClan,
  getClan,
  lookupClan,
  updateClan,
  type Clan,
  type CreateClanInput,
  type UpdateClanInput,
} from '@/api/endpoints/clans.ts';
import { clansQueryKey } from '@/hooks/useClanMemberships.ts';
import { useActiveClanStore } from '@/stores/useActiveClanStore.ts';

/**
 * Clan server state (doc 07 §5).
 *
 * Keys follow the convention of doc 07 §5: `['clan', clanId, …]`. Everything that changes a clan
 * invalidates both the clan itself and the membership list behind the switcher, because a rename or
 * a deletion is visible in both places and a switcher showing a name nobody uses any more is the
 * kind of small wrongness people lose trust over.
 */

export function clanQueryKey(clanId: string) {
  return ['clan', clanId] as const;
}

export function useClan(clanId: string | null) {
  return useQuery({
    queryKey: clanQueryKey(clanId ?? 'none'),
    queryFn: ({ signal }) => getClan(clanId ?? '', signal),
    enabled: clanId !== null,
    staleTime: 60_000,
  });
}

export function useCreateClan() {
  const queryClient = useQueryClient();
  const setActiveClan = useActiveClanStore((state) => state.setActiveClan);

  return useMutation({
    mutationFn: (input: CreateClanInput) => createClan(input),
    onSuccess: (clan: Clan) => {
      queryClient.setQueryData(clanQueryKey(clan.id), clan);
      void queryClient.invalidateQueries({ queryKey: clansQueryKey });
      // Somebody who has just founded a clan is working in it; making them pick it from the
      // switcher first would be a step with one possible answer.
      setActiveClan(clan.id);
    },
  });
}

export function useUpdateClan(clanId: string) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (input: UpdateClanInput) => updateClan(clanId, input),
    onSuccess: (clan: Clan) => {
      queryClient.setQueryData(clanQueryKey(clanId), clan);
      void queryClient.invalidateQueries({ queryKey: clansQueryKey });
    },
  });
}

export function useDeleteClan(clanId: string) {
  const queryClient = useQueryClient();
  const setActiveClan = useActiveClanStore((state) => state.setActiveClan);

  return useMutation({
    mutationFn: () => deleteClan(clanId),
    onSuccess: () => {
      queryClient.removeQueries({ queryKey: clanQueryKey(clanId) });
      void queryClient.invalidateQueries({ queryKey: clansQueryKey });
      // `useActiveClan` would fall back on its own, but clearing here avoids one render in
      // which the shell still points at a clan that no longer exists.
      setActiveClan(null);
    },
  });
}

/**
 * The preview card for a join code.
 *
 * Not a query keyed on the code: it runs when somebody presses "look it up", and caching a lookup
 * by code would keep telling them a clan is joinable after they have joined it.
 */
export function useLookupClan() {
  return useMutation({
    mutationFn: (code: string) => lookupClan(code),
  });
}
