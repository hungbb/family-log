import { App as AntApp, Badge, Button, Input, Select, Table, Tabs, Tag } from 'antd';
import dayjs from 'dayjs';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useNavigate, useParams } from 'react-router';

import { flattenBranches } from '@/api/endpoints/branches.ts';
import { type Member } from '@/api/endpoints/members.ts';
import { useBranches } from '@/features/branch';
import { useActiveClan } from '@/hooks/useActiveClan.ts';
import { toMessage } from '@/lib/apiErrors.ts';

import { JoinRequestInbox } from '../components/JoinRequestInbox';
import {
  useClanJoinRequests,
  useLeaveClan,
  useMembers,
  useRemoveMember,
} from '../hooks/useMembers';

import {
  FilterBar,
  MemberEmail,
  MemberName,
  Page,
  PageHeader,
  PageSubtitle,
  PageTitle,
  SectionCard,
} from './styles';

/**
 * The member list and the approver's inbox (F2.7, F2.9).
 *
 * Two tabs rather than two routes: they are the same subject — who is in this family — seen once as
 * a roll and once as a queue, and an approver moves between them constantly.
 *
 * The inbox tab appears only for somebody who can act on it, and its badge is the reason: an
 * administrator opening this page should see straight away that three relatives are waiting.
 */
export function MembersPage() {
  const { t } = useTranslation();
  const { message, modal } = AntApp.useApp();
  const navigate = useNavigate();
  const { clanId = '' } = useParams();
  const { can } = useActiveClan();

  const [search, setSearch] = useState('');
  const [branchId, setBranchId] = useState<string | undefined>(undefined);
  const [page, setPage] = useState(0);

  const filters = {
    ...(search !== '' ? { search } : {}),
    ...(branchId !== undefined ? { branchId } : {}),
    page,
    size: 20,
  };
  const { data: members, isPending } = useMembers(clanId, filters);
  const { data: branches } = useBranches(clanId);
  const canAdminister = can('CLAN_ADMIN');
  const { data: pending } = useClanJoinRequests(canAdminister ? clanId : '', 'PENDING');

  const removeMember = useRemoveMember(clanId);
  const leaveClan = useLeaveClan(clanId);

  const branchOptions = flattenBranches(branches ?? []).map((branch) => ({
    value: branch.id,
    label: `${'  '.repeat(branch.depth)}${branch.name}`,
  }));

  const onRemove = (member: Member) => {
    modal.confirm({
      title: t('members.remove.confirmTitle', { name: member.displayName ?? '' }),
      content: t('members.remove.confirmBody'),
      okText: t('members.remove.confirmOk'),
      cancelText: t('common.cancel'),
      okButtonProps: { danger: true },
      onOk: async () => {
        try {
          await removeMember.mutateAsync(member.userId);
          void message.success(t('members.remove.done'));
        } catch (failure) {
          void message.error(toMessage(failure, t));
        }
      },
    });
  };

  const onLeave = () => {
    modal.confirm({
      title: t('members.leave.confirmTitle'),
      content: t('members.leave.confirmBody'),
      okText: t('members.leave.confirmOk'),
      cancelText: t('common.cancel'),
      okButtonProps: { danger: true },
      onOk: async () => {
        try {
          await leaveClan.mutateAsync();
          void message.success(t('members.leave.done'));
          void navigate('/');
        } catch (failure) {
          void message.error(toMessage(failure, t));
        }
      },
    });
  };

  const memberList = (
    <>
      <FilterBar>
        <Input.Search
          allowClear
          placeholder={t('members.searchPlaceholder')}
          onSearch={(value) => {
            setPage(0);
            setSearch(value.trim());
          }}
        />
        <Select
          allowClear
          showSearch
          optionFilterProp="label"
          placeholder={t('members.allBranches')}
          value={branchId}
          options={branchOptions}
          onChange={(value: string | undefined) => {
            setPage(0);
            setBranchId(value);
          }}
        />
      </FilterBar>

      <Table<Member>
        rowKey="userId"
        loading={isPending}
        dataSource={members?.content ?? []}
        pagination={{
          current: page + 1,
          pageSize: members?.size ?? 20,
          total: members?.totalElements ?? 0,
          showSizeChanger: false,
          onChange: (next) => {
            setPage(next - 1);
          },
        }}
        columns={[
          {
            title: t('members.column.name'),
            dataIndex: 'displayName',
            render: (_value, member) => (
              <>
                <MemberName>{member.displayName ?? member.userId}</MemberName>
                {member.email !== undefined && <MemberEmail>{member.email}</MemberEmail>}
              </>
            ),
          },
          {
            title: t('members.column.branch'),
            dataIndex: 'homeBranchName',
            render: (_value, member) => member.homeBranchName ?? t('members.noBranch'),
          },
          {
            title: t('members.column.joined'),
            dataIndex: 'joinedAt',
            render: (_value, member) => dayjs(member.joinedAt).format('YYYY-MM-DD'),
          },
          {
            title: t('members.column.role'),
            dataIndex: 'isCreator',
            render: (_value, member) =>
              member.isCreator ? <Tag color="processing">{t('members.founder')}</Tag> : null,
          },
          {
            title: t('members.column.actions'),
            key: 'actions',
            render: (_value, member) =>
              canAdminister && !member.isCreator ? (
                <Button
                  size="small"
                  danger
                  type="text"
                  onClick={() => {
                    onRemove(member);
                  }}
                >
                  {t('members.remove.action')}
                </Button>
              ) : null,
          },
        ]}
      />
    </>
  );

  const pendingCount = pending?.totalElements ?? 0;

  return (
    <Page>
      <PageHeader>
        <div>
          <PageTitle>{t('members.title')}</PageTitle>
          <PageSubtitle>{t('members.subtitle')}</PageSubtitle>
        </div>
        <Button danger onClick={onLeave} loading={leaveClan.isPending}>
          {t('members.leave.action')}
        </Button>
      </PageHeader>

      <SectionCard>
        <Tabs
          items={[
            { key: 'members', label: t('members.tab.members'), children: memberList },
            ...(canAdminister
              ? [
                  {
                    key: 'requests',
                    label: (
                      <Badge count={pendingCount} size="small" offset={[10, 0]}>
                        {t('members.tab.requests')}
                      </Badge>
                    ),
                    children: <JoinRequestInbox clanId={clanId} branches={branches ?? []} />,
                  },
                ]
              : []),
          ]}
        />
      </SectionCard>
    </Page>
  );
}
