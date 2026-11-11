import { useMutation, useQuery, useQueryClient, type QueryClient } from '@tanstack/react-query';

import {
  addParentage,
  attachChildToUnion,
  createPerson,
  createUnion,
  deletePerson,
  deleteUnion,
  getPerson,
  listDeletedPersons,
  movePersonToBranch,
  patchPerson,
  removeParentage,
  restorePerson,
  searchPersons,
  updateUnion,
  type AddParentageInput,
  type CreatePersonInput,
  type CreateUnionInput,
  type PersonFilters,
  type UpdatePersonInput,
  type UpdateUnionInput,
} from '@/api/endpoints/persons.ts';
import { getTree, getTreeStats, type TreeQuery } from '@/api/endpoints/tree.ts';

/**
 * Lineage server state (doc 07 §5).
 *
 * **Every mutation invalidates the whole tree.** That looks heavy-handed and is the right call:
 * adding one edge can restamp the đời of a hundred descendants, moving a person changes who may edit
 * them, and a soft delete removes a node from a payload the client has already assembled. Patching
 * any of that locally would mean reimplementing rules the server has just applied — and being wrong
 * the first time somebody attaches a subtree.
 */

export function treeQueryKey(clanId: string, query: TreeQuery = {}) {
  return ['clan', clanId, 'tree', query.rootPersonId ?? 'all', query.depth ?? 'full'] as const;
}

export function personQueryKey(clanId: string, personId: string) {
  return ['clan', clanId, 'person', personId] as const;
}

export function personSearchQueryKey(clanId: string, filters: PersonFilters) {
  return ['clan', clanId, 'persons', filters] as const;
}

export function useTree(clanId: string, query: TreeQuery = {}) {
  return useQuery({
    queryKey: treeQueryKey(clanId, query),
    queryFn: ({ signal }) => getTree(clanId, query, signal),
    enabled: clanId !== '',
    staleTime: 30_000,
  });
}

export function useTreeStats(clanId: string) {
  return useQuery({
    queryKey: ['clan', clanId, 'tree', 'stats'],
    queryFn: ({ signal }) => getTreeStats(clanId, signal),
    enabled: clanId !== '',
    staleTime: 60_000,
  });
}

export function usePerson(clanId: string, personId: string | null) {
  return useQuery({
    queryKey: personQueryKey(clanId, personId ?? ''),
    queryFn: ({ signal }) => getPerson(clanId, personId ?? '', signal),
    enabled: clanId !== '' && personId !== null,
  });
}

export function usePersonSearch(clanId: string, filters: PersonFilters, enabled = true) {
  return useQuery({
    queryKey: personSearchQueryKey(clanId, filters),
    queryFn: ({ signal }) => searchPersons(clanId, filters, signal),
    enabled: enabled && clanId !== '',
    staleTime: 10_000,
  });
}

export function useDeletedPersons(clanId: string, enabled: boolean) {
  return useQuery({
    queryKey: ['clan', clanId, 'persons', 'deleted'],
    queryFn: ({ signal }) => listDeletedPersons(clanId, signal),
    enabled: enabled && clanId !== '',
  });
}

/**
 * Invalidates everything lineage-shaped in one clan.
 *
 * One helper rather than a list of keys per mutation: the prefix `['clan', clanId]` covers the tree,
 * the search pages, every person detail and the stats, and a mutation that forgot one of them would
 * leave a screen showing a family that no longer exists.
 */
function useInvalidateLineage(clanId: string) {
  const queryClient = useQueryClient();
  return () => queryClient.invalidateQueries({ queryKey: ['clan', clanId] });
}

export function useCreatePerson(clanId: string) {
  const invalidate = useInvalidateLineage(clanId);
  return useMutation({
    mutationFn: (input: CreatePersonInput) => createPerson(clanId, input),
    onSuccess: invalidate,
  });
}

export function useUpdatePerson(clanId: string) {
  const invalidate = useInvalidateLineage(clanId);
  return useMutation({
    mutationFn: (variables: { personId: string; input: UpdatePersonInput }) =>
      patchPerson(clanId, variables.personId, variables.input),
    onSuccess: invalidate,
  });
}

export function useDeletePerson(clanId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (personId: string) => deletePerson(clanId, personId),
    onSuccess: (_removed, personId) => invalidateAfterRemoval(queryClient, clanId, personId),
  });
}

/**
 * What removing a person invalidates: everything lineage-shaped in the clan — except the detail of
 * the person who was just removed.
 *
 * That one query is still mounted when this runs: the drawer that issued the delete closes only
 * after the mutation resolves. Invalidating it with the rest made the drawer ask the server, one
 * last time, for somebody the server had just been told to hide — a guaranteed
 * `404 PERSON_NOT_FOUND` in the console after every successful removal. Nothing is lost by
 * skipping it: the drawer is about to close, and if the person is restored and opened again the
 * detail is fetched afresh like any other.
 */
export function invalidateAfterRemoval(
  queryClient: QueryClient,
  clanId: string,
  personId: string,
): Promise<void> {
  const removed = JSON.stringify(personQueryKey(clanId, personId));
  return queryClient.invalidateQueries({
    queryKey: ['clan', clanId],
    predicate: (query) => JSON.stringify(query.queryKey) !== removed,
  });
}

export function useRestorePerson(clanId: string) {
  const invalidate = useInvalidateLineage(clanId);
  return useMutation({
    mutationFn: (personId: string) => restorePerson(clanId, personId),
    onSuccess: invalidate,
  });
}

export function useMovePersonToBranch(clanId: string) {
  const invalidate = useInvalidateLineage(clanId);
  return useMutation({
    mutationFn: (variables: { personId: string; branchId: string }) =>
      movePersonToBranch(clanId, variables.personId, variables.branchId),
    onSuccess: invalidate,
  });
}

export function useAddParentage(clanId: string) {
  const invalidate = useInvalidateLineage(clanId);
  return useMutation({
    mutationFn: (input: AddParentageInput) => addParentage(clanId, input),
    onSuccess: invalidate,
  });
}

export function useRemoveParentage(clanId: string) {
  const invalidate = useInvalidateLineage(clanId);
  return useMutation({
    mutationFn: (parentageId: string) => removeParentage(clanId, parentageId),
    onSuccess: invalidate,
  });
}

export function useCreateUnion(clanId: string) {
  const invalidate = useInvalidateLineage(clanId);
  return useMutation({
    mutationFn: (input: CreateUnionInput) => createUnion(clanId, input),
    onSuccess: invalidate,
  });
}

export function useUpdateUnion(clanId: string) {
  const invalidate = useInvalidateLineage(clanId);
  return useMutation({
    mutationFn: (variables: { unionId: string; input: UpdateUnionInput }) =>
      updateUnion(clanId, variables.unionId, variables.input),
    onSuccess: invalidate,
  });
}

export function useDeleteUnion(clanId: string) {
  const invalidate = useInvalidateLineage(clanId);
  return useMutation({
    mutationFn: (unionId: string) => deleteUnion(clanId, unionId),
    onSuccess: invalidate,
  });
}

export function useAttachChildToUnion(clanId: string) {
  const invalidate = useInvalidateLineage(clanId);
  return useMutation({
    mutationFn: (variables: { unionId: string; personId: string }) =>
      attachChildToUnion(clanId, variables.unionId, variables.personId),
    onSuccess: invalidate,
  });
}
