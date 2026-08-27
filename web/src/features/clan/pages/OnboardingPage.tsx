import { PlusOutlined, TeamOutlined } from '@ant-design/icons';
import { Button, Skeleton } from 'antd';
import { useTranslation } from 'react-i18next';
import { Navigate, useNavigate } from 'react-router';

import { useActiveClan } from '@/hooks/useActiveClan.ts';

import {
  ChoiceBody,
  ChoiceCard,
  ChoiceGrid,
  ChoiceIcon,
  ChoiceTitle,
  Page,
  PageHeader,
  PageSubtitle,
  PageTitle,
} from './styles';

/**
 * The empty state for somebody who belongs to no clan (F2.1).
 *
 * Two doors, deliberately the same size. Which one a person wants depends entirely on whether
 * anybody in their family has done this before, and the product has no way to guess — so it does
 * not pretend to, and neither is styled as the primary action.
 *
 * Anyone who <i>does</i> belong to a clan is sent straight into it: this screen has nothing to
 * offer them, and a landing page they have to click through is a landing page in the way.
 */
export function OnboardingPage() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const { clanId, isLoading } = useActiveClan();

  if (isLoading) {
    return (
      <Page>
        <Skeleton active />
      </Page>
    );
  }

  if (clanId !== null) {
    return <Navigate to={`/clans/${clanId}/members`} replace />;
  }

  return (
    <Page>
      <PageHeader>
        <div>
          <PageTitle>{t('clan.onboarding.title')}</PageTitle>
          <PageSubtitle>{t('clan.onboarding.subtitle')}</PageSubtitle>
        </div>
      </PageHeader>

      <ChoiceGrid>
        <ChoiceCard>
          <ChoiceIcon aria-hidden="true">
            <PlusOutlined />
          </ChoiceIcon>
          <ChoiceTitle>{t('clan.onboarding.create.title')}</ChoiceTitle>
          <ChoiceBody>{t('clan.onboarding.create.body')}</ChoiceBody>
          <Button type="primary" size="large" onClick={() => void navigate('/clans/new')}>
            {t('clan.onboarding.create.action')}
          </Button>
        </ChoiceCard>

        <ChoiceCard>
          <ChoiceIcon aria-hidden="true">
            <TeamOutlined />
          </ChoiceIcon>
          <ChoiceTitle>{t('clan.onboarding.join.title')}</ChoiceTitle>
          <ChoiceBody>{t('clan.onboarding.join.body')}</ChoiceBody>
          <Button size="large" onClick={() => void navigate('/join')}>
            {t('clan.onboarding.join.action')}
          </Button>
        </ChoiceCard>
      </ChoiceGrid>
    </Page>
  );
}
