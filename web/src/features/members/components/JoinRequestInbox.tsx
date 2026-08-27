import { App as AntApp, Button, Empty, Input, List, Modal, Select, Skeleton, Tag } from 'antd';
import dayjs from 'dayjs';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';

import { flattenBranches, type Branch } from '@/api/endpoints/branches.ts';
import { type JoinRequest } from '@/api/endpoints/joinRequests.ts';
import { toMessage } from '@/lib/apiErrors.ts';

import {
  useApproveJoinRequest,
  useClanJoinRequests,
  useRejectJoinRequest,
} from '../hooks/useMembers';
import { FieldRow, InlineActions, RequestMessage, RequestMeta } from '../pages/styles';

interface JoinRequestInboxProps {
  clanId: string;
  /** The clan's branches, so an approver can place the new member without a second request. */
  branches: Branch[];
}

/**
 * The approver's inbox (F2.7).
 *
 * Approving asks where the person goes, because that is the decision — "should this relative be in
 * the record" is usually easy, and "which *chi* are they from" is the part that needs a human. A
 * request made with a branch code arrives with the answer already filled in.
 *
 * Rejecting asks for a reason, and the field is required. The person being refused is a relative,
 * and a bare "no" is one they will simply send again.
 */
export function JoinRequestInbox({ clanId, branches }: JoinRequestInboxProps) {
  const { t } = useTranslation();
  const { message } = AntApp.useApp();
  const { data, isPending } = useClanJoinRequests(clanId, 'PENDING');
  const approve = useApproveJoinRequest(clanId);
  const reject = useRejectJoinRequest(clanId);

  const [deciding, setDeciding] = useState<JoinRequest | null>(null);
  const [rejecting, setRejecting] = useState<JoinRequest | null>(null);
  const [homeBranchId, setHomeBranchId] = useState<string | undefined>(undefined);
  const [reason, setReason] = useState('');

  const branchOptions = flattenBranches(branches)
    .filter((branch) => !branch.archived)
    .map((branch) => ({
      value: branch.id,
      // The indent makes the hierarchy legible in a flat select without a tree component.
      label: `${'  '.repeat(branch.depth)}${branch.name}`,
    }));

  const openApprove = (request: JoinRequest) => {
    setHomeBranchId(request.targetBranchId);
    setDeciding(request);
  };

  const onApprove = async () => {
    if (deciding === null) return;
    try {
      await approve.mutateAsync({
        requestId: deciding.id,
        ...(homeBranchId !== undefined ? { homeBranchId } : {}),
      });
      void message.success(t('members.inbox.approved', { name: deciding.requesterName ?? '' }));
      setDeciding(null);
    } catch (failure) {
      void message.error(toMessage(failure, t));
    }
  };

  const onReject = async () => {
    if (rejecting === null) return;
    try {
      await reject.mutateAsync({ requestId: rejecting.id, reason });
      void message.success(t('members.inbox.rejected'));
      setRejecting(null);
      setReason('');
    } catch (failure) {
      void message.error(toMessage(failure, t));
    }
  };

  if (isPending) {
    return <Skeleton active />;
  }

  const requests = data?.content ?? [];
  if (requests.length === 0) {
    return <Empty description={t('members.inbox.empty')} />;
  }

  return (
    <>
      <List
        dataSource={requests}
        renderItem={(request) => (
          <List.Item key={request.id}>
            <List.Item.Meta
              title={request.requesterName ?? request.requesterEmail ?? request.requesterUserId}
              description={
                <>
                  <RequestMeta>
                    {[
                      request.requesterEmail,
                      request.targetBranchName ?? t('members.inbox.wholeClan'),
                      dayjs(request.createdAt).format('YYYY-MM-DD'),
                    ]
                      .filter((part) => part !== undefined && part !== '')
                      .join(' · ')}
                  </RequestMeta>
                  {request.message !== undefined && (
                    <RequestMessage>{request.message}</RequestMessage>
                  )}
                  <InlineActions>
                    <Button
                      type="primary"
                      size="small"
                      onClick={() => {
                        openApprove(request);
                      }}
                    >
                      {t('members.inbox.approve')}
                    </Button>
                    <Button
                      size="small"
                      danger
                      onClick={() => {
                        setReason('');
                        setRejecting(request);
                      }}
                    >
                      {t('members.inbox.reject')}
                    </Button>
                  </InlineActions>
                </>
              }
            />
            <Tag color="warning">{t('members.status.PENDING')}</Tag>
          </List.Item>
        )}
      />

      <Modal
        open={deciding !== null}
        title={t('members.inbox.approveTitle', { name: deciding?.requesterName ?? '' })}
        okText={t('members.inbox.approve')}
        cancelText={t('common.cancel')}
        confirmLoading={approve.isPending}
        onOk={() => void onApprove()}
        onCancel={() => {
          setDeciding(null);
        }}
      >
        <p>{t('members.inbox.approveBody')}</p>
        <FieldRow>
          <Select
            allowClear
            showSearch
            optionFilterProp="label"
            placeholder={t('members.inbox.wholeClan')}
            value={homeBranchId}
            options={branchOptions}
            onChange={(value: string | undefined) => {
              setHomeBranchId(value);
            }}
          />
        </FieldRow>
      </Modal>

      <Modal
        open={rejecting !== null}
        title={t('members.inbox.rejectTitle')}
        okText={t('members.inbox.reject')}
        okButtonProps={{ danger: true, disabled: reason.trim() === '' }}
        cancelText={t('common.cancel')}
        confirmLoading={reject.isPending}
        onOk={() => void onReject()}
        onCancel={() => {
          setRejecting(null);
        }}
      >
        <p>{t('members.inbox.rejectBody')}</p>
        <Input.TextArea
          rows={3}
          maxLength={1000}
          showCount
          value={reason}
          onChange={(event) => {
            setReason(event.target.value);
          }}
        />
      </Modal>
    </>
  );
}
