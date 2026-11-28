import { QueryClient, QueryObserver } from '@tanstack/react-query';
import { describe, expect, it } from 'vitest';

import { invalidateAfterUnlink, myLinkedNodeQueryKey, propagationQueryKey } from './useClaims';

const CLAN = 'clan-1';
const OTHER_CLAN = 'clan-2';

function isInvalidated(queryClient: QueryClient, queryKey: readonly unknown[]): boolean {
  return queryClient.getQueryState(queryKey)?.isInvalidated ?? false;
}

/** A cache holding what the drawer has loaded when the linked user unlinks themselves. */
function loadedCache(): QueryClient {
  const queryClient = new QueryClient();
  queryClient.setQueryData(myLinkedNodeQueryKey(CLAN), { id: 'me' });
  queryClient.setQueryData(['clan', CLAN, 'person', 'me', 'link'], { linkedToCaller: true });
  queryClient.setQueryData(propagationQueryKey(CLAN, 'me'), { propagateAvatar: false });
  queryClient.setQueryData(propagationQueryKey(CLAN, 'sibling'), { propagateAvatar: false });
  queryClient.setQueryData(['clan', OTHER_CLAN, 'myLinkedNode'], { id: 'elsewhere' });
  return queryClient;
}

describe('invalidateAfterUnlink', () => {
  it('marks the rest of the clan stale, so the drawer learns the node is no longer yours', async () => {
    const queryClient = loadedCache();

    await invalidateAfterUnlink(queryClient, CLAN, 'me');

    expect(isInvalidated(queryClient, myLinkedNodeQueryKey(CLAN))).toBe(true);
    expect(isInvalidated(queryClient, ['clan', CLAN, 'person', 'me', 'link'])).toBe(true);
    expect(isInvalidated(queryClient, propagationQueryKey(CLAN, 'sibling'))).toBe(true);
  });

  it("marks the unlinked node's flags stale too, so a later link reads them fresh", async () => {
    const queryClient = loadedCache();

    await invalidateAfterUnlink(queryClient, CLAN, 'me');

    expect(isInvalidated(queryClient, propagationQueryKey(CLAN, 'me'))).toBe(true);
  });

  it('does not refetch those flags, which the caller may no longer read', async () => {
    const queryClient = loadedCache();
    let fetches = 0;
    // An active observer, as the still-mounted self-service form is. Fresh data, so subscribing
    // fetches nothing; any fetch counted below is the invalidation's doing.
    const observer = new QueryObserver(queryClient, {
      queryKey: propagationQueryKey(CLAN, 'me'),
      queryFn: () => ++fetches,
      staleTime: Infinity,
    });
    const unsubscribe = observer.subscribe(() => undefined);

    await invalidateAfterUnlink(queryClient, CLAN, 'me');

    expect(fetches).toBe(0);
    unsubscribe();
  });

  it('leaves other clans alone', async () => {
    const queryClient = loadedCache();

    await invalidateAfterUnlink(queryClient, CLAN, 'me');

    expect(isInvalidated(queryClient, ['clan', OTHER_CLAN, 'myLinkedNode'])).toBe(false);
  });
});
