import { DownOutlined } from '@ant-design/icons';
import { App as AntApp, Button, Dropdown, Input, Select, Table, Tag } from 'antd';
import dayjs from 'dayjs';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';

import type { AdminUser } from '@/api/endpoints/admin.ts';
import type { UserStatus } from '@/api/endpoints/auth.ts';
import { useAuth } from '@/hooks/useAuth.ts';
import { toMessage } from '@/lib/apiErrors.ts';

import { useAdminUsers, useUserAction, type UserAction } from '../hooks/useAdmin';
import { availableActions } from '../model/userActions';
import { FilterBar, StatusFilter, UserEmail, UserName } from '../pages/styles';

const STATUSES: UserStatus[] = ['ACTIVE', 'PENDING_VERIFICATION', 'SUSPENDED', 'DEACTIVATED'];

const STATUS_COLOR: Record<UserStatus, string> = {
  ACTIVE: 'success',
  PENDING_VERIFICATION: 'warning',
  SUSPENDED: 'error',
  DEACTIVATED: 'default',
};

/** Actions that change what someone can do, and so ask first. */
const CONFIRMED: ReadonlySet<UserAction> = new Set(['suspend', 'verifyEmail']);

/**
 * Every account on the platform, with what a super admin can do to each.
 *
 * Nothing here shows or sets a password. A forgotten password gets a reset link sent to the
 * holder's own inbox, so only the holder ever knows the new one.
 */
export function UsersPanel() {
  const { t } = useTranslation();
  const { message, modal } = AntApp.useApp();
  const { user: caller } = useAuth();

  const [search, setSearch] = useState('');
  const [status, setStatus] = useState<UserStatus | undefined>(undefined);
  const [page, setPage] = useState(0);

  const { data, isFetching } = useAdminUsers({
    ...(search !== '' ? { search } : {}),
    ...(status !== undefined ? { status } : {}),
    page,
    size: 20,
  });
  const userAction = useUserAction();

  const run = async (action: UserAction, user: AdminUser) => {
    try {
      await userAction.mutateAsync({ action, userId: user.id });
      void message.success(t(`admin.users.action.${action}.done`, { email: user.email }));
    } catch (failure) {
      void message.error(toMessage(failure, t));
    }
  };

  const onAction = (action: UserAction, user: AdminUser) => {
    if (!CONFIRMED.has(action)) {
      void run(action, user);
      return;
    }
    modal.confirm({
      title: t(`admin.users.action.${action}.confirmTitle`, { email: user.email }),
      content: t(`admin.users.action.${action}.confirmBody`),
      okText: t(`admin.users.action.${action}.label`),
      cancelText: t('common.cancel'),
      okButtonProps: { danger: action === 'suspend' },
      onOk: () => run(action, user),
    });
  };

  return (
    <>
      <FilterBar>
        <Input.Search
          allowClear
          placeholder={t('admin.users.searchPlaceholder')}
          onSearch={(value) => {
            setPage(0);
            setSearch(value.trim());
          }}
        />
        <StatusFilter>
          <Select
            allowClear
            placeholder={t('admin.users.allStatuses')}
            value={status}
            options={STATUSES.map((value) => ({ value, label: t(`admin.users.status.${value}`) }))}
            onChange={(value: UserStatus | undefined) => {
              setPage(0);
              setStatus(value);
            }}
          />
        </StatusFilter>
      </FilterBar>

      <Table<AdminUser>
        rowKey="id"
        loading={isFetching}
        dataSource={data?.content ?? []}
        scroll={{ x: true }}
        pagination={{
          current: page + 1,
          pageSize: data?.size ?? 20,
          total: data?.totalElements ?? 0,
          showSizeChanger: false,
          onChange: (next) => {
            setPage(next - 1);
          },
        }}
        columns={[
          {
            title: t('admin.users.column.user'),
            key: 'user',
            render: (_value, user) => (
              <>
                <UserName>{user.displayName}</UserName>
                <UserEmail>{user.email}</UserEmail>
              </>
            ),
          },
          {
            title: t('admin.users.column.status'),
            key: 'status',
            render: (_value, user) => (
              <>
                <Tag color={STATUS_COLOR[user.status]}>
                  {t(`admin.users.status.${user.status}`)}
                </Tag>
                {user.superAdmin && <Tag color="processing">{t('admin.users.superAdmin')}</Tag>}
              </>
            ),
          },
          {
            title: t('admin.users.column.lastLogin'),
            key: 'lastLoginAt',
            render: (_value, user) =>
              user.lastLoginAt === undefined
                ? t('admin.users.never')
                : dayjs(user.lastLoginAt).format('YYYY-MM-DD HH:mm'),
          },
          {
            title: t('admin.users.column.created'),
            key: 'createdAt',
            render: (_value, user) => dayjs(user.createdAt).format('YYYY-MM-DD'),
          },
          {
            title: t('admin.users.column.actions'),
            key: 'actions',
            render: (_value, user) => {
              const actions = availableActions(user, caller?.id);
              if (actions.length === 0) {
                return null;
              }
              return (
                <Dropdown
                  trigger={['click']}
                  menu={{
                    items: actions.map((action) => ({
                      key: action,
                      label: t(`admin.users.action.${action}.label`),
                      danger: action === 'suspend',
                    })),
                    onClick: ({ key }) => {
                      onAction(key as UserAction, user);
                    },
                  }}
                >
                  <Button
                    size="small"
                    loading={userAction.isPending && userAction.variables.userId === user.id}
                  >
                    {t('admin.users.actions')} <DownOutlined />
                  </Button>
                </Dropdown>
              );
            },
          },
        ]}
      />
    </>
  );
}
