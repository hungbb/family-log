import { App as AntApp, Alert, Button, Result, Select, Table, Tag } from 'antd';
import dayjs from 'dayjs';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useParams } from 'react-router';

import { type ClanPermission } from '@/api/endpoints/clans.ts';
import { type Grant } from '@/api/endpoints/permissions.ts';
import { PermissionGate } from '@/components/feedback/PermissionGate';
import { useMembers } from '@/features/members/hooks/useMembers.ts';
import {
  useGrants,
  useMyPermissions,
  usePermissions,
  useRevokeGrant,
} from '@/hooks/usePermissions.ts';
import { toMessage } from '@/lib/apiErrors.ts';

import { GrantDialog } from '../components/GrantDialog';
import { describeGrant } from '../model/describe';

import {
  FieldRow,
  FilterBar,
  HolderEmail,
  HolderName,
  Meta,
  Page,
  PageHeader,
  PageSubtitle,
  PageTitle,
  ScopeSummary,
  SectionCard,
} from './styles';

/** Every permission the catalogue has, for the filter. Doc 04 §2, in the order it lists them. */
const ALL_PERMISSIONS: ClanPermission[] = [
  'CLAN_VIEW',
  'TREE_EDIT',
  'MEMBER_APPROVE',
  'NODE_CLAIM_APPROVE',
  'ROLE_MANAGE',
  'CLAN_ADMIN',
];

/**
 * The permissions screen (F3.3).
 *
 * Who holds what, where, granted by whom, and until when — the table a family opens when somebody
 * asks "why can he change that?". Every row carries the plain-language summary rather than only the
 * enum, for the reason in {@code describeGrant}: `TREE_EDIT / BRANCH / Chi Hai` is precise and
 * tells a person nothing.
 *
 * Somebody without `ROLE_MANAGE` gets an explanation rather than an empty table (F3.7): the server
 * answers 403 here, and a screen that rendered "no results" would be quietly lying about why.
 */
export function PermissionsPage() {
  const { t } = useTranslation();
  const { message, modal } = AntApp.useApp();
  const { clanId = '' } = useParams();
  const { can, isLoading: permissionsLoading } = usePermissions(clanId);
  const { data: mine } = useMyPermissions(clanId);

  const [userId, setUserId] = useState<string | undefined>(undefined);
  const [permission, setPermission] = useState<ClanPermission | undefined>(undefined);
  const [granting, setGranting] = useState(false);

  const mayManage = can('ROLE_MANAGE');
  const filters = {
    ...(userId !== undefined ? { userId } : {}),
    ...(permission !== undefined ? { permission } : {}),
  };

  const { data: grants, isPending } = useGrants(mayManage ? clanId : '', filters);
  const { data: members } = useMembers(clanId, { page: 0, size: 100 });
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
          // LAST_ADMIN_PROTECTED lands here, and its message is the one that matters most on this
          // screen: it is the only refusal a clan administrator will meet by doing the right thing.
          void message.error(toMessage(failure, t));
        }
      },
    });
  };

  if (!permissionsLoading && !mayManage) {
    // F3.7: name what they *do* hold rather than showing a bare error. Most of the support
    // conversations this product would otherwise have start exactly here.
    return (
      <Page>
        <Result
          status="403"
          title={t('permissions.denied.title')}
          subTitle={t('permissions.denied.body')}
          extra={
            <Alert
              type="info"
              showIcon
              message={t('permissions.denied.youHold')}
              description={(mine?.grants ?? []).map((grant) => describeGrant(grant, t)).join(' · ')}
            />
          }
        />
      </Page>
    );
  }

  return (
    <Page>
      <PageHeader>
        <div>
          <PageTitle>{t('permissions.title')}</PageTitle>
          <PageSubtitle>{t('permissions.subtitle')}</PageSubtitle>
        </div>
        <PermissionGate permission="ROLE_MANAGE" clanId={clanId} mode="disable">
          <Button
            type="primary"
            onClick={() => {
              setGranting(true);
            }}
          >
            {t('permissions.grant.action')}
          </Button>
        </PermissionGate>
      </PageHeader>

      <SectionCard>
        <FilterBar>
          <FieldRow>
            <Select
              allowClear
              showSearch
              optionFilterProp="label"
              placeholder={t('permissions.filter.anyone')}
              value={userId}
              onChange={setUserId}
              options={(members?.content ?? []).map((member) => ({
                value: member.userId,
                label: member.displayName ?? member.email ?? member.userId,
              }))}
            />
          </FieldRow>
          <FieldRow>
            <Select
              allowClear
              placeholder={t('permissions.filter.anyPermission')}
              value={permission}
              onChange={setPermission}
              options={ALL_PERMISSIONS.map((name) => ({
                value: name,
                label: t(`permissions.name.${name}`),
              }))}
            />
          </FieldRow>
        </FilterBar>

        <Table<Grant>
          rowKey="id"
          loading={isPending}
          dataSource={grants ?? []}
          pagination={false}
          columns={[
            {
              title: t('permissions.column.holder'),
              dataIndex: 'granteeName',
              render: (_value, grant) => (
                <>
                  <HolderName>{grant.granteeName ?? grant.granteeUserId}</HolderName>
                  {grant.granteeEmail !== undefined && (
                    <HolderEmail>{grant.granteeEmail}</HolderEmail>
                  )}
                </>
              ),
            },
            {
              title: t('permissions.column.permission'),
              dataIndex: 'permission',
              render: (_value, grant) => (
                <Tag {...(grant.permission === 'CLAN_ADMIN' ? { color: 'processing' } : {})}>
                  {t(`permissions.name.${grant.permission}`)}
                </Tag>
              ),
            },
            {
              title: t('permissions.column.scope'),
              dataIndex: 'scopeLabel',
              render: (_value, grant) => <ScopeSummary>{describeGrant(grant, t)}</ScopeSummary>,
            },
            {
              title: t('permissions.column.granted'),
              dataIndex: 'grantedAt',
              render: (_value, grant) => (
                <>
                  <Meta>{dayjs(grant.grantedAt).format('YYYY-MM-DD')}</Meta>
                  {grant.grantedByName !== undefined && (
                    <HolderEmail>{grant.grantedByName}</HolderEmail>
                  )}
                </>
              ),
            },
            {
              title: t('permissions.column.expires'),
              dataIndex: 'expiresAt',
              render: (_value, grant) =>
                grant.expiresAt === undefined ? (
                  <Meta>{t('permissions.noExpiry')}</Meta>
                ) : (
                  <Meta>{dayjs(grant.expiresAt).format('YYYY-MM-DD')}</Meta>
                ),
            },
            {
              title: t('permissions.column.actions'),
              key: 'actions',
              render: (_value, grant) => (
                <PermissionGate permission="ROLE_MANAGE" clanId={clanId}>
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
                </PermissionGate>
              ),
            },
          ]}
        />
      </SectionCard>

      <GrantDialog
        clanId={clanId}
        open={granting}
        onClose={() => {
          setGranting(false);
        }}
        members={members?.content ?? []}
      />
    </Page>
  );
}
