import { App as AntApp, Alert, DatePicker, Form, Modal, Select } from 'antd';
import type { Dayjs } from 'dayjs';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';

import { type ClanPermission } from '@/api/endpoints/clans.ts';
import { type Member } from '@/api/endpoints/members.ts';
import { type CreateGrantInput, type ScopeType } from '@/api/endpoints/permissions.ts';
import { useCreateGrant, useGrantable } from '@/hooks/usePermissions.ts';
import { toMessage } from '@/lib/apiErrors.ts';

import { FieldRow } from '../pages/styles';

import { CLAN_SCOPE_VALUE, ScopePicker } from './ScopePicker';

/**
 * Granting a permission (F3.4).
 *
 * <b>The form is built from `/permissions/grantable`, and from nothing else.</b> That is the whole
 * design: a branch role manager opening this dialog sees only the permissions they themselves hold
 * and only the branches they manage, so an escalation is not an option the UI declines — it is one
 * it cannot express. The server still refuses one that arrives by another route (doc 04 §5.4); this
 * is what stops anybody discovering that the hard way.
 *
 * The scope list is re-derived when the permission changes, because the two are not independent:
 * somebody may hold `TREE_EDIT` over two chi and `ROLE_MANAGE` over only one.
 */
interface GrantDialogProps {
  clanId: string;
  open: boolean;
  onClose: () => void;
  members: Member[];
  /** Pre-selects a person, for the "grant…" button inside a member's drawer. */
  presetUserId?: string;
}

export function GrantDialog(props: GrantDialogProps) {
  // Remounted whenever the dialog opens or closes, so the form starts empty without an effect that
  // resets four pieces of state — which React rightly calls a cascading render, and which would
  // also mean the form briefly showing the last grant somebody made.
  return <GrantForm key={String(props.open)} {...props} />;
}

function GrantForm({ clanId, open, onClose, members, presetUserId }: GrantDialogProps) {
  const { t } = useTranslation();
  const { message } = AntApp.useApp();
  const { data: grantable, isPending } = useGrantable(clanId, open);
  const createGrant = useCreateGrant(clanId);

  const [granteeUserId, setGranteeUserId] = useState<string | undefined>(presetUserId);
  const [permission, setPermission] = useState<ClanPermission | undefined>(undefined);
  const [scopeValue, setScopeValue] = useState<string | undefined>(undefined);
  const [expiresAt, setExpiresAt] = useState<Dayjs | null>(null);

  const offered = grantable?.permissions ?? [];
  const scopes = offered.find((entry) => entry.permission === permission)?.scopes ?? [];

  const scopeType: ScopeType = scopeValue === CLAN_SCOPE_VALUE ? 'CLAN' : 'BRANCH';
  const ready = granteeUserId !== undefined && permission !== undefined && scopeValue !== undefined;

  const submit = async () => {
    if (!ready) return;

    const input: CreateGrantInput = {
      granteeUserId,
      permission,
      scopeType,
      ...(scopeType === 'BRANCH' ? { scopeRefId: scopeValue } : {}),
      ...(expiresAt !== null ? { expiresAt: expiresAt.toISOString() } : {}),
    };

    try {
      await createGrant.mutateAsync(input);
      void message.success(t('permissions.grant.done'));
      onClose();
    } catch (failure) {
      void message.error(toMessage(failure, t));
    }
  };

  return (
    <Modal
      open={open}
      title={t('permissions.grant.title')}
      okText={t('permissions.grant.submit')}
      cancelText={t('common.cancel')}
      okButtonProps={{ disabled: !ready }}
      confirmLoading={createGrant.isPending}
      onOk={() => void submit()}
      onCancel={onClose}
      destroyOnHidden
    >
      {!isPending && offered.length === 0 && (
        // Reachable: somebody holding ROLE_MANAGE and nothing else has a scope to manage but
        // nothing to hand out in it. Saying so beats an empty form.
        <Alert type="info" showIcon message={t('permissions.grant.nothingToGrant')} />
      )}

      <Form layout="vertical">
        <Form.Item label={t('permissions.grant.member')} required>
          <FieldRow>
            <Select
              showSearch
              optionFilterProp="label"
              placeholder={t('permissions.grant.memberPlaceholder')}
              value={granteeUserId}
              onChange={setGranteeUserId}
              options={members.map((member) => ({
                value: member.userId,
                label: member.displayName ?? member.email ?? member.userId,
              }))}
            />
          </FieldRow>
        </Form.Item>

        <Form.Item label={t('permissions.grant.permission')} required>
          <FieldRow>
            <Select
              loading={isPending}
              placeholder={t('permissions.grant.permissionPlaceholder')}
              // `?? null` rather than `undefined`: `exactOptionalPropertyTypes` treats an explicit
              // undefined as a value, and AntD's Select spells "nothing chosen" as null.
              value={permission ?? null}
              onChange={(next: ClanPermission) => {
                setPermission(next);
                // The scopes depend on the permission, so a scope chosen for the previous one is
                // not necessarily still on offer.
                setScopeValue(undefined);
              }}
              options={offered.map((entry) => ({
                value: entry.permission,
                label: t(`permissions.name.${entry.permission}`),
                title: t(`permissions.description.${entry.permission}`),
              }))}
            />
          </FieldRow>
        </Form.Item>

        <Form.Item
          label={t('permissions.grant.scope')}
          required
          extra={permission === undefined ? t('permissions.grant.scopeAfterPermission') : undefined}
        >
          <ScopePicker
            scopes={scopes}
            subtreeAvailable={grantable?.subtreeAvailable ?? false}
            value={scopeValue}
            onChange={setScopeValue}
            disabled={permission === undefined}
          />
        </Form.Item>

        <Form.Item
          label={t('permissions.grant.expires')}
          extra={t('permissions.grant.expiresHint')}
        >
          <FieldRow>
            <DatePicker value={expiresAt} onChange={setExpiresAt} />
          </FieldRow>
        </Form.Item>
      </Form>
    </Modal>
  );
}
