import { api } from '../client';

import type { LocaleTag, UserStatus } from './auth';

/**
 * `/me` — doc 05 §4.2.
 *
 * Every one of these acts on the signed-in account and takes no id, which is what makes them safe
 * without a permission check: there is no way to name someone else's account.
 */

export interface MeProfile {
  id: string;
  email: string;
  displayName: string;
  locale: LocaleTag;
  status: UserStatus;
  emailVerified: boolean;
  emailVerifiedAt?: string;
  avatarMediaId?: string;
  lastLoginAt?: string;
  createdAt: string;
}

/** One live session, as the profile page lists it. No token, by design. */
export interface SessionSummary {
  id: string;
  userAgent?: string;
  ipAddress?: string;
  issuedAt: string;
  expiresAt: string;
}

/** An absent field is left unchanged — the server reads absence as "no change". */
export interface UpdateProfileInput {
  displayName?: string;
  locale?: LocaleTag;
}

export function getMe(signal?: AbortSignal): Promise<MeProfile> {
  return api.get<MeProfile>('/me', signal ? { signal } : {});
}

export function updateMe(input: UpdateProfileInput): Promise<MeProfile> {
  return api.patch<MeProfile>('/me', input);
}

export function getSessions(signal?: AbortSignal): Promise<SessionSummary[]> {
  return api.get<SessionSummary[]>('/me/sessions', signal ? { signal } : {});
}
