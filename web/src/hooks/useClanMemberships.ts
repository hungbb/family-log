import { useQuery } from '@tanstack/react-query';

import { listClans, type ClanSummary } from '@/api/endpoints/clans.ts';

/**
 * The clans the signed-in account belongs to (FR-C8).
 *
 * Lives in `hooks/` rather than inside the clan feature because the app shell reads it — the clan
 * switcher sits in the top bar, and `components/` may not import from `features/` (doc 07 §2).
 *
 * One query key for the whole application, so a clan created in one screen appears in the switcher
 * in another without either of them knowing about the other.
 */
export const clansQueryKey = ['clans'] as const;

export function useMyClans() {
  return useQuery({
    queryKey: clansQueryKey,
    queryFn: ({ signal }) => listClans(signal),
    // A minute: memberships change when somebody is approved, and the approval already
    // invalidates this key. The stale time is for reloads, not for correctness.
    staleTime: 60_000,
  });
}

export type { ClanSummary };
