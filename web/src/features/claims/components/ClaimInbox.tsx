import { App as AntApp, Button, Empty, Input, List, Modal, Skeleton, Tag } from 'antd';
import dayjs from 'dayjs';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';

import { type NodeClaim } from '@/api/endpoints/claims.ts';
import { toMessage } from '@/lib/apiErrors.ts';

import { useApproveClaim, useClaims, useRejectClaim } from '../hooks/useClaims';
import { Evidence, InlineActions, Meta, TreeContext } from '../pages/styles';

import { describeFamily } from './ClaimDialog';

interface ClaimInboxProps {
  clanId: string;
}

/**
 * The approver's claim inbox (F6.5).
 *
 * What an approver is really being asked is "is this the same person?", and the answer is almost
 * never in the claim itself — it is in the family around the node. So each row leads with the
 * node's parents and children, then the requester, then whatever they wrote.
 *
 * Approving links the account to the node and nothing else. It does **not** hand out edit rights,
 * and the confirmation says so, because an approver who believes otherwise is an approver who will
 * refuse claims to be safe.
 *
 * Rejecting requires a reason. The person refused is a relative who will otherwise ask again.
 */
export function ClaimInbox({ clanId }: ClaimInboxProps) {
  const { t } = useTranslation();
  const { message } = AntApp.useApp();
  const { data, isPending } = useClaims(clanId, 'PENDING');
  const approve = useApproveClaim(clanId);
  const reject = useRejectClaim(clanId);

  const [approving, setApproving] = useState<NodeClaim | null>(null);
  const [rejecting, setRejecting] = useState<NodeClaim | null>(null);
  const [reason, setReason] = useState('');

  const onApprove = async () => {
    if (approving === null) return;
    try {
      await approve.mutateAsync(approving.id);
      void message.success(
        t('claims.inbox.approved', {
          person: approving.personName ?? '',
          name: approving.requesterName ?? '',
        }),
      );
      setApproving(null);
    } catch (failure) {
      void message.error(toMessage(failure, t));
    }
  };

  const onReject = async () => {
    if (rejecting === null) return;
    try {
      await reject.mutateAsync({ claimId: rejecting.id, reason: reason.trim() });
      void message.success(t('claims.inbox.rejected'));
      setRejecting(null);
      setReason('');
    } catch (failure) {
      void message.error(toMessage(failure, t));
    }
  };

  if (isPending) {
    return <Skeleton active />;
  }

  const claims = data?.content ?? [];
  if (claims.length === 0) {
    return <Empty description={t('claims.inbox.empty')} />;
  }

  return (
    <>
      <List
        dataSource={claims}
        renderItem={(claim) => (
          <List.Item key={claim.id}>
            <List.Item.Meta
              title={t('claims.inbox.rowTitle', {
                name: claim.requesterName ?? claim.requesterEmail ?? claim.requesterUserId,
                person: claim.personName ?? '',
              })}
              description={
                <>
                  <Meta>
                    {[
                      claim.requesterEmail,
                      claim.personGeneration !== undefined
                        ? t('claims.browse.generationOption', { n: claim.personGeneration })
                        : undefined,
                      dayjs(claim.createdAt).format('YYYY-MM-DD'),
                    ]
                      .filter((part) => part !== undefined && part !== '')
                      .join(' · ')}
                  </Meta>
                  <TreeContext>
                    {describeFamily(
                      claim,
                      t('claims.candidate.parents'),
                      t('claims.candidate.children'),
                    ) || t('claims.candidate.noFamily')}
                  </TreeContext>
                  {claim.evidence !== undefined && <Evidence>{claim.evidence}</Evidence>}
                  <InlineActions>
                    <Button
                      type="primary"
                      size="small"
                      onClick={() => {
                        setApproving(claim);
                      }}
                    >
                      {t('claims.inbox.approve')}
                    </Button>
                    <Button
                      size="small"
                      danger
                      onClick={() => {
                        setReason('');
                        setRejecting(claim);
                      }}
                    >
                      {t('claims.inbox.reject')}
                    </Button>
                  </InlineActions>
                </>
              }
            />
            <Tag color="warning">{t('claims.status.PENDING')}</Tag>
          </List.Item>
        )}
      />

      <Modal
        open={approving !== null}
        title={t('claims.inbox.approveTitle', { person: approving?.personName ?? '' })}
        okText={t('claims.inbox.approve')}
        cancelText={t('common.cancel')}
        confirmLoading={approve.isPending}
        onOk={() => void onApprove()}
        onCancel={() => {
          setApproving(null);
        }}
      >
        <p>
          {t('claims.inbox.approveBody', {
            name: approving?.requesterName ?? '',
            person: approving?.personName ?? '',
          })}
        </p>
        {/* Said plainly, because it is the single thing approvers most often get wrong. */}
        <p>{t('claims.inbox.approveNoEditRights')}</p>
      </Modal>

      <Modal
        open={rejecting !== null}
        title={t('claims.inbox.rejectTitle')}
        okText={t('claims.inbox.reject')}
        okButtonProps={{ danger: true, disabled: reason.trim() === '' }}
        cancelText={t('common.cancel')}
        confirmLoading={reject.isPending}
        onOk={() => void onReject()}
        onCancel={() => {
          setRejecting(null);
        }}
      >
        <p>{t('claims.inbox.rejectBody')}</p>
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
