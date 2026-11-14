import type { AdminUser } from '@/api/endpoints/admin.ts';

import type { UserAction } from '../hooks/useAdmin';

/**
 * Which actions a row offers.
 *
 * The same rules the server enforces, written down once so the menu never offers a button that
 * can only answer 409 or 403. The server still decides: this list only decides what is shown.
 */
export function availableActions(user: AdminUser, callerId: string | undefined): UserAction[] {
  const actions: UserAction[] = [];
  const canSignIn = user.status === 'ACTIVE' || user.status === 'PENDING_VERIFICATION';

  if (!user.emailVerified) {
    actions.push('verifyEmail', 'resendVerification');
  }
  if (canSignIn) {
    actions.push('sendPasswordReset');
  }
  if (canSignIn && !user.superAdmin && user.id !== callerId) {
    actions.push('suspend');
  }
  if (user.status === 'SUSPENDED') {
    actions.push('reactivate');
  }
  return actions;
}
