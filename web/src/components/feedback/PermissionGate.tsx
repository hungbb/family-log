import { Tooltip } from 'antd';
import { cloneElement, isValidElement, type ReactElement, type ReactNode } from 'react';
import { useTranslation } from 'react-i18next';

import { type ClanPermission } from '@/api/endpoints/clans.ts';
import { useActiveClan } from '@/hooks/useActiveClan.ts';
import { usePermissions } from '@/hooks/usePermissions.ts';

/**
 * Hides or disables an affordance the caller cannot use (F3.2, doc 07 §8).
 *
 * <b>This is a convenience, never the security boundary.</b> Everything it gates is re-decided
 * server-side on every request (doc 04 §5.1). Its job is to stop somebody spending an afternoon on a
 * button that was always going to answer 403 — not to keep them out, which the server does whatever
 * this component renders.
 *
 * Two modes, and the choice matters more than it looks:
 *
 * - `hide` (the default) removes the control. Right for actions that would be meaningless — a
 *   "revoke" button next to a grant somebody has nothing to do with.
 * - `disable` keeps it, greyed out, with a tooltip naming the permission it needs. Right for the
 *   things a family argues about, where "I cannot see that button" and "I am not allowed to press
 *   it" send people to very different conversations.
 *
 * @example
 * <PermissionGate permission="ROLE_MANAGE" mode="disable">
 *   <Button>Grant a permission</Button>
 * </PermissionGate>
 */
interface PermissionGateProps {
  /** What the caller must hold somewhere in the clan. */
  permission: ClanPermission;
  /**
   * The clan to ask about. Defaults to the shell's active clan, which is what a page inside it
   * wants; pass one explicitly on a screen that shows more than one clan.
   */
  clanId?: string;
  mode?: 'hide' | 'disable';
  /** Rendered instead when the caller lacks the permission and `mode` is `hide`. */
  fallback?: ReactNode;
  children: ReactNode;
}

export function PermissionGate({
  permission,
  clanId,
  mode = 'hide',
  fallback = null,
  children,
}: PermissionGateProps): ReactNode {
  const { t } = useTranslation();
  const { clanId: activeClanId } = useActiveClan();
  const { can, isLoading } = usePermissions(clanId ?? activeClanId);

  if (can(permission)) {
    return children;
  }

  // While the answer is unknown, hide rather than show. A control that appears a moment late is a
  // smaller irritation than one that appears and then vanishes — and in `disable` mode the greyed
  // form is the honest placeholder, since it is what an unpermitted caller will end up seeing.
  if (mode === 'hide' || isLoading) {
    return fallback;
  }

  // `disabled` is set on the child rather than by wrapping it, so the control keeps its place in
  // the layout and AntD keeps styling it as the control it is.
  const disabled = isValidElement(children)
    ? cloneElement(children as ReactElement<{ disabled?: boolean }>, { disabled: true })
    : children;

  return (
    <Tooltip
      title={t('permissions.gate.requires', { permission: t(`permissions.name.${permission}`) })}
    >
      <span>{disabled}</span>
    </Tooltip>
  );
}
