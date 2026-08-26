import { api } from '../client';

/**
 * `/auth` — doc 05 §4.1.
 *
 * Everything here sets `skipAuth`. These endpoints are where a session comes from, so they want no
 * bearer token; more importantly, it stops a 401 from `/auth/refresh` triggering another refresh,
 * which would be an infinite loop rather than a recovery.
 *
 * The types are hand-written against doc 05 rather than generated. `openapi-typescript` and the
 * `gen:api` script of doc 07 §4.4 land with the CI workstream that will diff them; until something
 * checks the generated output, a generator is a build step that can silently drift.
 */

/** BCP 47 tags the API accepts. Mirrors `LocaleTag` on the backend. */
export type LocaleTag = 'vi-VN' | 'en-US';

export type UserStatus = 'ACTIVE' | 'PENDING_VERIFICATION' | 'SUSPENDED' | 'DEACTIVATED';

/** The account, as returned alongside a token pair. Never carries a credential. */
export interface UserSummary {
  id: string;
  email: string;
  displayName: string;
  locale: LocaleTag;
  status: UserStatus;
  emailVerified: boolean;
  avatarMediaId?: string;
}

export interface TokenPair {
  accessToken: string;
  refreshToken: string;
  /** Seconds until the access token expires — used to renew ahead of a failure, not after one. */
  expiresIn: number;
  user: UserSummary;
}

export interface RegisterInput {
  email: string;
  password: string;
  displayName: string;
  locale?: LocaleTag;
}

export interface RegisterResult {
  userId: string;
  status: UserStatus;
}

export function register(input: RegisterInput): Promise<RegisterResult> {
  return api.post<RegisterResult>('/auth/register', input, { skipAuth: true });
}

export function login(input: { email: string; password: string }): Promise<TokenPair> {
  return api.post<TokenPair>('/auth/login', input, { skipAuth: true });
}

/** Rotates the pair. The presented token is spent, whether or not this call succeeds. */
export function refreshSession(refreshToken: string): Promise<TokenPair> {
  return api.post<TokenPair>('/auth/refresh', { refreshToken }, { skipAuth: true });
}

export function logout(refreshToken: string): Promise<void> {
  return api.post<undefined>('/auth/logout', { refreshToken }, { skipAuth: true });
}

/** Ends every session for the account, this one included. Needs the bearer token. */
export function logoutAll(): Promise<void> {
  return api.post<undefined>('/auth/logout-all');
}

export function verifyEmail(token: string): Promise<void> {
  return api.post<undefined>('/auth/verify-email', { token }, { skipAuth: true });
}

/** Always resolves for a well-formed request, whether or not the address has an account. */
export function forgotPassword(email: string): Promise<void> {
  return api.post<undefined>('/auth/forgot-password', { email }, { skipAuth: true });
}

export function resetPassword(input: { token: string; newPassword: string }): Promise<void> {
  return api.post<undefined>('/auth/reset-password', input, { skipAuth: true });
}
