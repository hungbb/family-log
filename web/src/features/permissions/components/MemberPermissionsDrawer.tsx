import { App as AntApp, Button, Descriptions, Drawer, Empty, List, Tag } from 'antd';
import dayjs from 'dayjs';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';

import { type Member } from '@/api/endpoints/members.ts';
import { type Grant } from '@/api/endpoints/permissions.ts';
import { PermissionGate } from '@/components/feedback/PermissionGate';
import { useGrants, useRevokeGrant } from '@/hooks/usePermissions.ts';
import { toMessage } from '@/lib/apiErrors.ts';

import { describeGrant } from '../model/describe';
import { DrawerSection, DrawerSectionTitle, Meta, ScopeSummary } from '../pages/styles';

import { GrantDialog } from './GrantDialog';

/**
 * One member, and what they may do (F3.5).
 *
 * The summary line is the reason this exists. A table row saying `TREE_EDIT / BRANCH / Chi Hai` is
 * accurate and tells a family nothing; "Có thể sửa <i>Chi Hai</i> và mọi nhánh bên dưới" is the same
 * fact in words the person granting it can check. Doc 04 §3.1's isolation rule is about path
 * prefixes, and nobody outside the codebase should have to think in those terms.
 *
 * Opened from the member list. The grants come from `/permissions?userId=`, which needs
 * `ROLE_MANAGE`, so the drawer is only offered to somebody who has it.
 */
interface MemberPermissionsDrawerProps {
  clanId: string;
  member: Member | null;
  members: Member[];
  onClose: () => void;
}

export function MemberPermissionsDrawer({
  clanId,
  member,
  members,
  onClose,
}: MemberPermissionsDrawerProps) {
  const { t } = useTranslation();
  const { message, modal } = AntApp.useApp();
  const [granting, setGranting] = useState(false);

  const { data: grants, isPending } = useGrants(
    member === null ? '' : clanId,
    member === null ? {} : { userId: member.userId },
  );
  const revokeGrant = useRevokeGrant(clanId);

  const onRevoke = (grant: Grant) => {
    modal.confirm({
      title: t('permissions.revoke.confirmTitle'),
      content: describeGrant(grant, t),
      okText: t('permissions.revoke.confirmOk'),
      cancelText: t('common.cancel'),
      okButtonProps: { danger: true },
      onOk: async () => {
        try {
          await revokeGrant.mutateAsync(grant.id);
          void message.success(t('permissions.revoke.done'));
        } catch (failure) {
          void message.error(toMessage(failure, t));
        }
      },
    });
  };

  return (
    <Drawer
      open={member !== null}
      onClose={onClose}
      width={480}
      title={member?.displayName ?? t('permissions.drawer.title')}
      destroyOnHidden
    >
      {member !== null && (
        <DrawerSection>
          <Descriptions
            column={1}
            size="small"
            items={[
              { key: 'email', label: t('members.column.name'), children: member.email ?? '—' },
              {
                key: 'branch',
                label: t('members.column.branch'),
                children: member.homeBranchName ?? t('members.noBranch'),
              },
              {
                key: 'joined',
                label: t('members.column.joined'),
                children: dayjs(member.joinedAt).format('YYYY-MM-DD'),
              },
            ]}
          />

          <DrawerSectionTitle>{t('permissions.drawer.holds')}</DrawerSectionTitle>

          {!isPending && (grants ?? []).length === 0 ? (
            <Empty
              image={Empty.PRESENTED_IMAGE_SIMPLE}
              description={t('permissions.drawer.holdsNothing')}
            />
          ) : (
            <List
              loading={isPending}
              dataSource={grants ?? []}
              renderItem={(grant) => (
                <List.Item
                  key={grant.id}
                  actions={[
                    <PermissionGate key="revoke" permission="ROLE_MANAGE" clanId={clanId}>
                      <Button
                        size="small"
                        danger
                        type="text"
                        onClick={() => {
                          onRevoke(grant);
                        }}
                      >
                        {t('permissions.revoke.action')}
                      </Button>
                    </PermissionGate>,
                  ]}
                >
                  <List.Item.Meta
                    title={
                      <>
                        <Tag>{t(`permissions.name.${grant.permission}`)}</Tag>
                        {grant.expiresAt !== undefined && (
                          <Meta>
                            {t('permissions.expiresOn', {
                              date: dayjs(grant.expiresAt).format('YYYY-MM-DD'),
                            })}
                          </Meta>
                        )}
                      </>
                    }
                    description={
                      <>
                        <ScopeSummary>{describeGrant(grant, t)}</ScopeSummary>
                        {grant.grantedByName !== undefined && (
                          <Meta>
                            {t('permissions.grantedBy', {
                              name: grant.grantedByName,
                              date: dayjs(grant.grantedAt).format('YYYY-MM-DD'),
                            })}
                          </Meta>
                        )}
                      </>
                    }
                  />
                </List.Item>
              )}
            />
          )}

          <PermissionGate permission="ROLE_MANAGE" clanId={clanId}>
            <Button
              type="primary"
              onClick={() => {
                setGranting(true);
              }}
            >
              {t('permissions.grant.action')}
            </Button>
          </PermissionGate>

          <GrantDialog
            clanId={clanId}
            open={granting}
            onClose={() => {
              setGranting(false);
            }}
            members={members}
            presetUserId={member.userId}
          />
        </DrawerSection>
      )}
    </Drawer>
  );
}
