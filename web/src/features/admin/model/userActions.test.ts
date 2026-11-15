import { describe, expect, it } from 'vitest';

import type { AdminUser } from '@/api/endpoints/admin.ts';

import { availableActions } from './userActions';

function user(overrides: Partial<AdminUser>): AdminUser {
  return {
    id: 'u1',
    email: 'an@example.com',
    displayName: 'An',
    locale: 'vi-VN',
    status: 'ACTIVE',
    emailVerified: true,
    createdAt: '2026-01-01T00:00:00Z',
    superAdmin: false,
    ...overrides,
  };
}

describe('availableActions', () => {
  it('offers a verified, active member a reset and a suspension', () => {
    expect(availableActions(user({}), 'me')).toEqual(['sendPasswordReset', 'suspend']);
  });

  it('offers an unverified member verification first', () => {
    expect(
      availableActions(user({ emailVerified: false, status: 'PENDING_VERIFICATION' }), 'me'),
    ).toEqual(['verifyEmail', 'resendVerification', 'sendPasswordReset', 'suspend']);
  });

  it('never offers to suspend a super admin, or the caller themselves', () => {
    expect(availableActions(user({ superAdmin: true }), 'me')).not.toContain('suspend');
    expect(availableActions(user({ id: 'me' }), 'me')).not.toContain('suspend');
  });

  it('offers a suspended account only reactivation, and no reset it could not use', () => {
    expect(availableActions(user({ status: 'SUSPENDED' }), 'me')).toEqual(['reactivate']);
  });
});
