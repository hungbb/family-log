import { QueryClient } from '@tanstack/react-query';
import { describe, expect, it } from 'vitest';

import {
  invalidateAfterRemoval,
  personQueryKey,
  personSearchQueryKey,
  treeQueryKey,
} from './useTree';

const CLAN = 'clan-1';
const OTHER_CLAN = 'clan-2';

/** A cache holding what the tree page has loaded when somebody opens a person and removes them. */
function loadedCache(): QueryClient {
  const queryClient = new QueryClient();
  queryClient.setQueryData(treeQueryKey(CLAN), { persons: [] });
  queryClient.setQueryData(['clan', CLAN, 'tree', 'stats'], { personCount: 2 });
  queryClient.setQueryData(personSearchQueryKey(CLAN, { page: 0, size: 20 }), { content: [] });
  queryClient.setQueryData(personQueryKey(CLAN, 'removed'), { id: 'removed' });
  queryClient.setQueryData(personQueryKey(CLAN, 'sibling'), { id: 'sibling' });
  queryClient.setQueryData(personQueryKey(OTHER_CLAN, 'removed'), { id: 'removed' });
  return queryClient;
}

function isInvalidated(queryClient: QueryClient, queryKey: readonly unknown[]): boolean {
  return queryClient.getQueryState(queryKey)?.isInvalidated ?? false;
}

describe('invalidateAfterRemoval', () => {
  it('does not refetch the person who was just removed — the server would answer 404', async () => {
    const queryClient = loadedCache();

    await invalidateAfterRemoval(queryClient, CLAN, 'removed');

    expect(isInvalidated(queryClient, personQueryKey(CLAN, 'removed'))).toBe(false);
  });

  it('still invalidates everything else the removal can have changed', async () => {
    const queryClient = loadedCache();

    await invalidateAfterRemoval(queryClient, CLAN, 'removed');

    expect(isInvalidated(queryClient, treeQueryKey(CLAN))).toBe(true);
    expect(isInvalidated(queryClient, ['clan', CLAN, 'tree', 'stats'])).toBe(true);
    expect(isInvalidated(queryClient, personSearchQueryKey(CLAN, { page: 0, size: 20 }))).toBe(
      true,
    );
    // A relative's detail lists the removed person among their children or siblings.
    expect(isInvalidated(queryClient, personQueryKey(CLAN, 'sibling'))).toBe(true);
  });

  it('leaves another clan alone, even for the same id', async () => {
    const queryClient = loadedCache();

    await invalidateAfterRemoval(queryClient, CLAN, 'removed');

    expect(isInvalidated(queryClient, personQueryKey(OTHER_CLAN, 'removed'))).toBe(false);
  });
});
