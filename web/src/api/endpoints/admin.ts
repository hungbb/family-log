import { api } from '../client';

import type { LocaleTag, UserStatus } from './auth';
import type { Page } from './members';

/**
 * `/admin/users` — platform user administration, for super admins only.
 *
 * Every call here answers `403 PERMISSION_DENIED` to anyone else. The screens are hidden from
 * other people as a courtesy, but the server makes the decision.
 */

/** One account, as the user list shows it. Never carries a credential. */
export interface AdminUser {
  id: string;
  email: string;
  displayName: string;
  locale: LocaleTag;
  status: UserStatus;
  emailVerified: boolean;
  lastLoginAt?: string;
  createdAt: string;
  /** A super admin cannot be suspended. */
  superAdmin: boolean;
}

export interface AdminUserFilters {
  search?: string;
  status?: UserStatus;
  page?: number;
  size?: number;
}

export function listAdminUsers(
  filters: AdminUserFilters,
  signal?: AbortSignal,
): Promise<Page<AdminUser>> {
  return api.get<Page<AdminUser>>('/admin/users', {
    query: {
      search: filters.search,
      status: filters.status,
      page: filters.page ?? 0,
      size: filters.size ?? 20,
    },
    ...(signal ? { signal } : {}),
  });
}

export function suspendUser(userId: string): Promise<AdminUser> {
  return api.post<AdminUser>(`/admin/users/${userId}/suspend`);
}

export function reactivateUser(userId: string): Promise<AdminUser> {
  return api.post<AdminUser>(`/admin/users/${userId}/reactivate`);
}

export function markUserEmailVerified(userId: string): Promise<AdminUser> {
  return api.post<AdminUser>(`/admin/users/${userId}/verify-email`);
}

export function resendUserVerification(userId: string): Promise<void> {
  return api.post<undefined>(`/admin/users/${userId}/resend-verification`);
}

export function sendUserPasswordReset(userId: string): Promise<void> {
  return api.post<undefined>(`/admin/users/${userId}/send-password-reset`);
}
