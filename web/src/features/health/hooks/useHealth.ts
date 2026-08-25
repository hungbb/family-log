import { useQuery } from '@tanstack/react-query';

import { getHealth } from '../../../api/endpoints/health';

/** Query key namespace, so a later `invalidateQueries(['health'])` is unambiguous. */
export const healthQueryKey = ['health'] as const;

/**
 * Polls the API's health endpoint.
 *
 * The `signal` is TanStack Query's — it aborts the request when the component unmounts or the
 * query is superseded, which is exactly why `request()` takes one.
 *
 * A failing health check is information, not an error to retry away: one retry, then show the
 * failure. Refetching every 30 seconds means the badge turns green on its own once the developer
 * starts the API, without a reload.
 */
export function useHealth() {
  return useQuery({
    queryKey: healthQueryKey,
    queryFn: ({ signal }) => getHealth(signal),
    retry: 1,
    staleTime: 10_000,
    refetchInterval: 30_000,
    refetchOnWindowFocus: true,
  });
}
