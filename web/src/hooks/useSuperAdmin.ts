import { useQuery } from '@tanstack/react-query';

import { getMe } from '@/api/endpoints/me.ts';

/**
 * Whether the signed-in account is a super admin, read from `/me`.
 *
 * Lives in `hooks/` because the shell needs it: the top bar's account menu offers Administration
 * only to someone who can use it, and `components/` may not import from `features/` (doc 07 §2).
 * It shares the `['me']` key with the profile page, so both read one cached response.
 *
 * Only a courtesy. Every `/admin` endpoint answers 403 to anyone else.
 */
export function useSuperAdmin() {
  const { data, isPending } = useQuery({
    queryKey: ['me'],
    queryFn: ({ signal }) => getMe(signal),
    staleTime: 60_000,
  });
  return { isSuperAdmin: data?.superAdmin === true, isPending };
}
