import { Alert, Badge, Button, Skeleton, Tabs } from 'antd';
import { useTranslation } from 'react-i18next';
import { useNavigate, useParams } from 'react-router';

import { useActiveClan } from '@/hooks/useActiveClan.ts';

import { ClaimInbox } from '../components/ClaimInbox';
import { UnlinkedNodeBrowser } from '../components/UnlinkedNodeBrowser';
import { useClaims, useMyLinkedNode } from '../hooks/useClaims';

import { Page, PageHeader, PageSubtitle, PageTitle, SectionCard } from './styles';

/**
 * Claiming a node, and answering other people's claims (F6.2, F6.5).
 *
 * Two tabs on one route, like the members page: "find myself" and "decide for others" are the same
 * subject seen from either end, and the approver is usually also somebody with a node of their own.
 * The inbox tab appears only for a relative who can act on it, with a badge — an administrator
 * opening this page should see at once that three people are waiting.
 *
 * Somebody who already holds a node sees that instead of the browser. There is nothing left for
 * them to claim (I-P4), and a search box that can only ever produce a 409 is a trap.
 */
export function ClaimsPage() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const { clanId = '' } = useParams();
  const { can } = useActiveClan();

  const canApprove = can('NODE_CLAIM_APPROVE');
  const { data: pending } = useClaims(canApprove ? clanId : '', 'PENDING');
  const { data: myNode, isPending } = useMyLinkedNode(clanId);

  const browser = isPending ? (
    <Skeleton active />
  ) : myNode !== null && myNode !== undefined ? (
    <Alert
      type="success"
      showIcon
      message={t('claims.browse.alreadyYours', { name: myNode.personName ?? '' })}
      description={t('claims.browse.alreadyYoursHelp')}
      action={
        <Button
          size="small"
          onClick={() => {
            // The tree's own toolbar has a "find me" control that jumps to the linked node
            // (F6.7), so this only has to get them there.
            void navigate(`/clans/${clanId}/tree`);
          }}
        >
          {t('claims.browse.openMyNode')}
        </Button>
      }
    />
  ) : (
    <UnlinkedNodeBrowser clanId={clanId} />
  );

  const pendingCount = pending?.totalElements ?? 0;

  return (
    <Page>
      <PageHeader>
        <div>
          <PageTitle>{t('claims.title')}</PageTitle>
          <PageSubtitle>{t('claims.subtitle')}</PageSubtitle>
        </div>
      </PageHeader>

      <SectionCard>
        <Tabs
          items={[
            { key: 'browse', label: t('claims.tab.browse'), children: browser },
            ...(canApprove
              ? [
                  {
                    key: 'inbox',
                    label: (
                      <Badge count={pendingCount} size="small" offset={[10, 0]}>
                        {t('claims.tab.inbox')}
                      </Badge>
                    ),
                    children: <ClaimInbox clanId={clanId} />,
                  },
                ]
              : []),
          ]}
        />
      </SectionCard>
    </Page>
  );
}
