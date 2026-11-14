import { api } from '../client';

import type { LocaleTag, UserStatus } from './auth';
import type { Page } from './members';

/**
 * `/admin/**` — platform administration, for super admins only.
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

export type MailSecurity = 'NONE' | 'STARTTLS' | 'SSL';

/** The outgoing mail settings. The SMTP password is write-only and never comes back. */
export interface MailSettings {
  enabled: boolean;
  host?: string;
  port?: number;
  username?: string;
  /** Whether an SMTP password is stored. */
  loginStored: boolean;
  security: MailSecurity;
  fromAddress?: string;
  fromName?: string;
  updatedAt: string;
}

/**
 * A full replacement of the settings.
 *
 * `password`: leave it out to keep the stored one, `''` to remove it, anything else to replace it.
 */
export interface MailSettingsInput {
  enabled: boolean;
  host: string | null;
  port: number | null;
  username: string | null;
  password?: string;
  security: MailSecurity;
  fromAddress: string | null;
  fromName: string | null;
}

export function getMailSettings(signal?: AbortSignal): Promise<MailSettings> {
  return api.get<MailSettings>('/admin/settings/mail', signal ? { signal } : {});
}

export function updateMailSettings(input: MailSettingsInput): Promise<MailSettings> {
  return api.put<MailSettings>('/admin/settings/mail', input);
}

export function sendTestMail(to: string): Promise<void> {
  return api.post<undefined>('/admin/settings/mail/test', { to });
}
