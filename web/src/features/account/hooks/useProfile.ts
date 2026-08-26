import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import {
  getMe,
  getSessions,
  updateMe,
  type MeProfile,
  type UpdateProfileInput,
} from '../../../api/endpoints/me';
import { useSessionStore } from '../../../stores/useSessionStore';

/**
 * The profile and its sessions, as server state (doc 07 §5).
 *
 * The store keeps a copy of the signed-in account so the shell can render an avatar without a
 * request, but the authority is here — the store is never written to from a component, only from
 * the mutation below, and only with what the server said it stored.
 */

export const profileQueryKey = ['me'] as const;
export const sessionsQueryKey = ['me', 'sessions'] as const;

export function useProfile() {
  return useQuery({
    queryKey: profileQueryKey,
    queryFn: ({ signal }) => getMe(signal),
    staleTime: 60_000,
  });
}

export function useSessions() {
  return useQuery({
    queryKey: sessionsQueryKey,
    queryFn: ({ signal }) => getSessions(signal),
    // A session list that is a minute stale is worse than useless: someone checking it is checking
    // for a session they do not recognise, right now.
    staleTime: 0,
    refetchOnWindowFocus: true,
  });
}

export function useUpdateProfile() {
  const queryClient = useQueryClient();
  const userUpdated = useSessionStore((state) => state.userUpdated);

  return useMutation({
    mutationFn: (input: UpdateProfileInput) => updateMe(input),
    onSuccess: (profile: MeProfile) => {
      queryClient.setQueryData(profileQueryKey, profile);
      // Keep the shell's copy in step, or the top bar shows the old name until the next reload.
      const current = useSessionStore.getState().user;
      if (current !== null) {
        userUpdated({
          ...current,
          displayName: profile.displayName,
          locale: profile.locale,
          status: profile.status,
          emailVerified: profile.emailVerified,
        });
      }
    },
  });
}
