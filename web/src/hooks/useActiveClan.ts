import { useEffect } from 'react';

import { type ClanPermission, type ClanSummary } from '@/api/endpoints/clans.ts';
import { useActiveClanStore } from '@/stores/useActiveClanStore.ts';

import { useMyClans } from './useClanMemberships.ts';

/**
 * The clan the shell is showing, validated against the caller's memberships (FR-C8).
 *
 * The store remembers an id across reloads; this is what decides whether that id still means
 * anything. Three cases, and all three matter:
 *
 * - the remembered clan is still one of theirs → keep it;
 * - it is not, and they have others → fall back to the first, rather than showing an empty shell;
 * - they have none at all → clear it, and the router sends them to onboarding.
 *
 * Doing this here rather than in a route loader means every consumer sees a validated id, and a
 * person who is removed from a clan in another tab stops seeing it here on the next refetch.
 */
export interface ActiveClan {
  /** Null while the membership list is loading, and when the caller belongs to no clan. */
  clan: ClanSummary | null;
  clanId: string | null;
  clans: ClanSummary[];
  isLoading: boolean;
  /** True when the caller belongs to no clan at all — the onboarding case (F2.1). */
  hasNoClans: boolean;
  setActiveClan: (clanId: string) => void;
  /** Whether the caller holds a permission in the active clan. UI gating only. */
  can: (permission: ClanPermission) => boolean;
}

export function useActiveClan(): ActiveClan {
  const { data: clans, isPending } = useMyClans();
  const storedClanId = useActiveClanStore((state) => state.clanId);
  const setActiveClan = useActiveClanStore((state) => state.setActiveClan);

  const available = clans ?? [];
  const remembered = available.find((clan) => clan.id === storedClanId) ?? null;
  const resolved = remembered ?? available[0] ?? null;

  useEffect(() => {
    if (isPending) return;
    // Reconcile the stored preference with what the server says the caller may actually open.
    const nextId = resolved?.id ?? null;
    if (storedClanId !== nextId) {
      setActiveClan(nextId);
    }
  }, [isPending, resolved, storedClanId, setActiveClan]);

  const permissions = resolved?.myPermissions ?? [];

  return {
    clan: resolved,
    clanId: resolved?.id ?? null,
    clans: available,
    isLoading: isPending,
    hasNoClans: !isPending && available.length === 0,
    setActiveClan,
    can: (permission) => permissions.includes(permission),
  };
}
