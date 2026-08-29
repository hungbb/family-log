import { App as AntApp, Button, List, Tag } from 'antd';
import dayjs from 'dayjs';
import { useTranslation } from 'react-i18next';

import { type ClaimStatus, type NodeClaim } from '@/api/endpoints/claims.ts';
import { toMessage } from '@/lib/apiErrors.ts';

import { useCancelClaim, useMyClaims } from '../hooks/useClaims';
import { Evidence, Meta } from '../pages/styles';

/**
 * The caller's own claims, across every clan (F6.4).
 *
 * Rendered on `/me` next to their join requests, for the same reason: somebody waiting on a
 * decision looks for it where their own things are, not inside the clan that has not yet let them
 * in. A rejected claim shows its reason — that is what requiring one was for.
 */
export function MyClaims() {
  const { t } = useTranslation();
  const { data, isPending } = useMyClaims();

  return (
    <List
      loading={isPending}
      dataSource={data ?? []}
      locale={{ emptyText: t('claims.mine.empty') }}
      renderItem={(claim) => <MyClaimRow key={claim.id} claim={claim} />}
    />
  );
}

/**
 * One claim.
 *
 * A component of its own because cancelling is clan-scoped on the server and the hook therefore
 * needs the row's own clan id — `/me/claims` spans clans, so there is no single active one the
 * list could pass down.
 */
function MyClaimRow({ claim }: { claim: NodeClaim }) {
  const { t } = useTranslation();
  const { message } = AntApp.useApp();
  const cancel = useCancelClaim(claim.clanId);

  const onCancel = async () => {
    try {
      await cancel.mutateAsync(claim.id);
      void message.success(t('claims.mine.cancelled'));
    } catch (failure) {
      void message.error(toMessage(failure, t));
    }
  };

  return (
    <List.Item
      actions={
        claim.status === 'PENDING'
          ? [
              <Button
                key="cancel"
                size="small"
                type="text"
                danger
                loading={cancel.isPending}
                onClick={() => void onCancel()}
              >
                {t('claims.mine.cancel')}
              </Button>,
            ]
          : []
      }
    >
      <List.Item.Meta
        title={claim.personName ?? claim.personId}
        description={
          <>
            <Meta>
              {[
                claim.clanName,
                t('claims.mine.sentOn', { date: dayjs(claim.createdAt).format('YYYY-MM-DD') }),
              ]
                .filter((part) => part !== undefined && part !== '')
                .join(' · ')}
            </Meta>
            {claim.decisionReason !== undefined && <Evidence>{claim.decisionReason}</Evidence>}
          </>
        }
      />
      <Tag color={statusColour(claim.status)}>{t(`claims.status.${claim.status}`)}</Tag>
    </List.Item>
  );
}

/**
 * The colour a status is shown in.
 *
 * An unknown value falls through to the neutral tag rather than crashing: doc 05 §6 allows enum
 * values to be added inside a major version.
 */
function statusColour(status: ClaimStatus): string {
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
