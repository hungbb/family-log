import { App as AntApp, Button, List, Tag } from 'antd';
import dayjs from 'dayjs';
import { useTranslation } from 'react-i18next';

import { type JoinRequestStatus } from '@/api/endpoints/joinRequests.ts';
import { toMessage } from '@/lib/apiErrors.ts';

import { useCancelJoinRequest, useMyJoinRequests } from '../hooks/useMembers';
import { RequestMeta } from '../pages/styles';

/**
 * The caller's own outbound join requests (F2.8).
 *
 * Rendered on `/me`, because that is where somebody who has asked to join a family and is waiting
 * will go looking — they are not a member of anything yet, so there is no clan-scoped page that
 * could hold it.
 *
 * A rejected request shows its reason. That is the whole point of requiring one.
 */
export function MyJoinRequests() {
  const { t } = useTranslation();
  const { message } = AntApp.useApp();
  const { data, isPending } = useMyJoinRequests();
  const cancel = useCancelJoinRequest();

  const onCancel = async (requestId: string) => {
    try {
      await cancel.mutateAsync(requestId);
      void message.success(t('members.myRequests.cancelled'));
    } catch (failure) {
      void message.error(toMessage(failure, t));
    }
  };

  return (
    <List
      loading={isPending}
      dataSource={data ?? []}
      locale={{ emptyText: t('members.myRequests.empty') }}
      renderItem={(request) => (
        <List.Item
          key={request.id}
          actions={
            request.status === 'PENDING'
              ? [
                  <Button
                    key="cancel"
                    size="small"
                    type="text"
                    danger
                    onClick={() => void onCancel(request.id)}
                  >
                    {t('members.myRequests.cancel')}
                  </Button>,
                ]
              : []
          }
        >
          <List.Item.Meta
            title={request.clanName}
            description={
              <>
                <RequestMeta>
                  {[
                    request.targetBranchName,
                    t('members.myRequests.sentOn', {
                      date: dayjs(request.createdAt).format('YYYY-MM-DD'),
                    }),
                  ]
                    .filter((part) => part !== undefined && part !== '')
                    .join(' · ')}
                </RequestMeta>
                {request.decisionReason !== undefined && (
                  <RequestMeta>{` — ${request.decisionReason}`}</RequestMeta>
                )}
              </>
            }
          />
          <Tag color={statusColour(request.status)}>{t(`members.status.${request.status}`)}</Tag>
        </List.Item>
      )}
    />
  );
}

/**
 * The colour a status is shown in.
 *
 * An unknown value falls through to the neutral tag rather than crashing: doc 05 §6 allows enum
 * values to be added inside a major version, and a client that breaks on one is a client that
 * breaks on a deploy.
 */
function statusColour(status: JoinRequestStatus): string {
  switch (status) {
    case 'PENDING':
      return 'warning';
    case 'APPROVED':
      return 'success';
    case 'REJECTED':
      return 'error';
    default:
      return 'default';
  }
}
