import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import {
  getMailSettings,
  listAdminUsers,
  markUserEmailVerified,
  reactivateUser,
  resendUserVerification,
  sendTestMail,
  sendUserPasswordReset,
  suspendUser,
  updateMailSettings,
  type AdminUserFilters,
  type MailSettings,
  type MailSettingsInput,
} from '@/api/endpoints/admin.ts';

/** Platform administration, as server state (doc 07 §5). */

const adminUsersKey = ['admin', 'users'] as const;
const mailSettingsKey = ['admin', 'settings', 'mail'] as const;

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

export function useMailSettings() {
  return useQuery({
    queryKey: mailSettingsKey,
    queryFn: ({ signal }) => getMailSettings(signal),
  });
}

export function useUpdateMailSettings() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (input: MailSettingsInput) => updateMailSettings(input),
    onSuccess: (saved: MailSettings) => {
      queryClient.setQueryData(mailSettingsKey, saved);
    },
  });
}

export function useSendTestMail() {
  return useMutation({ mutationFn: (to: string) => sendTestMail(to) });
}
