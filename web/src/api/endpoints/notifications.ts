import { api } from '../client';

import type { Page } from './members';

/**
 * In-app notifications — doc 05 §4.2 (F6.10).
 *
 * Not clan-scoped, and cannot be: a person's notifications span every family they belong to, and
 * the bell in the top bar is one list.
 */

export type NotificationType =
  'CLAIM_SUBMITTED' | 'CLAIM_DECIDED' | 'NODE_LINKED' | 'NODE_UNLINKED';

/**
 * One notification.
 *
 * `payload` is deliberately loose: the client builds an i18n key from `type` and interpolates the
 * payload into it, so the server never composes a sentence it would then have to translate. An
 * unknown `type` must render as a neutral line rather than crash (doc 05 §6).
 */
export interface AppNotification {
  id: string;
  clanId?: string;
  /**
   * One of {@link NotificationType} — typed as a plain string on purpose, because doc 05 §6 lets
   * the server add values inside a major version and a client that narrows to today's four would
   * fail to compile against tomorrow's fifth rather than falling back to a neutral line.
   */
  type: string;
  payload: Record<string, string | undefined>;
  /** Null while unread — the field the badge counts. */
  readAt?: string;
  createdAt: string;
}

export function listNotifications(
  unreadOnly: boolean,
  signal?: AbortSignal,
): Promise<Page<AppNotification>> {
  return api.get<Page<AppNotification>>('/me/notifications', {
    query: { unreadOnly, size: 20 },
    ...(signal ? { signal } : {}),
  });
}

/**
 * How many are unread.
 *
 * Its own endpoint rather than counting the list client-side: this is what a poll every sixty
 * seconds asks for, and it is one indexed count against the partial index the badge exists for.
 */
export function getUnreadCount(signal?: AbortSignal): Promise<{ unread: number }> {
  return api.get<{ unread: number }>('/me/notifications/unread-count', signal ? { signal } : {});
}

export function markNotificationRead(notificationId: string): Promise<void> {
  return api.post<undefined>(`/me/notifications/${notificationId}/read`);
}
