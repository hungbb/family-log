import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import {
  listAdminUsers,
  markUserEmailVerified,
  reactivateUser,
  resendUserVerification,
  sendUserPasswordReset,
  suspendUser,
  type AdminUserFilters,
} from '@/api/endpoints/admin.ts';

/** Platform administration, as server state (doc 07 §5). */

const adminUsersKey = ['admin', 'users'] as const;

export function useAdminUsers(filters: AdminUserFilters) {
  return useQuery({
    queryKey: [...adminUsersKey, filters],
    queryFn: ({ signal }) => listAdminUsers(filters, signal),
    staleTime: 15_000,
    placeholderData: (previous) => previous,
  });
}

export type UserAction =
  'suspend' | 'reactivate' | 'verifyEmail' | 'resendVerification' | 'sendPasswordReset';

const ACTIONS: Record<UserAction, (userId: string) => Promise<unknown>> = {
  suspend: suspendUser,
  reactivate: reactivateUser,
  verifyEmail: markUserEmailVerified,
  resendVerification: resendUserVerification,
  sendPasswordReset: sendUserPasswordReset,
};

/** One mutation for every row action. Each one can change a row, so all refresh the list. */
export function useUserAction() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (variables: { action: UserAction; userId: string }) =>
      ACTIONS[variables.action](variables.userId),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: adminUsersKey });
    },
  });
}
