import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import {
  getUnreadCount,
  listNotifications,
  markNotificationRead,
} from '../api/endpoints/notifications';

/**
 * In-app notifications, as server state (F6.10).
 *
 * The badge polls; the list does not. A count is one indexed row against a partial index, and it is
 * the only thing that has to stay fresh while somebody is looking at something else. The list is
 * fetched when the bell is opened, which is the moment its contents start mattering.
 *
 * Sixty seconds is the interval doc 07 §5 names. It is deliberately unhurried: this is a family
 * record, not a chat, and nothing here is worse for arriving a minute late.
 */

export const notificationsQueryKey = ['me', 'notifications'] as const;
export const unreadCountQueryKey = ['me', 'notifications', 'unreadCount'] as const;

export function useUnreadCount() {
  return useQuery({
    queryKey: unreadCountQueryKey,
    queryFn: ({ signal }) => getUnreadCount(signal),
    refetchInterval: 60_000,
    // Polling a background tab wakes a request nobody is waiting on; the focus refetch below
    // catches up the moment somebody comes back.
    refetchIntervalInBackground: false,
    refetchOnWindowFocus: true,
    staleTime: 30_000,
  });
}

export function useNotifications(enabled: boolean) {
  return useQuery({
    queryKey: notificationsQueryKey,
    queryFn: ({ signal }) => listNotifications(false, signal),
    enabled,
    staleTime: 30_000,
  });
}

export function useMarkRead() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (notificationId: string) => markNotificationRead(notificationId),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: notificationsQueryKey });
      void queryClient.invalidateQueries({ queryKey: unreadCountQueryKey });
    },
  });
}
