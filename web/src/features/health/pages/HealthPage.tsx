import { ReloadOutlined } from '@ant-design/icons';
import { Button, Divider } from 'antd';
import { useTranslation } from 'react-i18next';

import { isApiError } from '../../../api/errors';
import { useHealth } from '../hooks/useHealth';

import {
  Badge,
  DetailList,
  Details,
  Dot,
  Hint,
  Page,
  PageHeader,
  PageSubtitle,
  PageTitle,
  StatusCard,
  StatusRow,
} from './styles';

/**
 * The Phase 0 wire test, as a screen.
 *
 * It proves the whole path end to end: React renders through the themed shell, TanStack Query
 * drives the request, the `fetch` wrapper builds it and parses the answer, CORS lets the browser
 * read the response, and Spring answers from a database it really connected to. When this badge is
 * green, the foundation works — which is the only claim Phase 0 makes.
 */
export function HealthPage() {
  const { t } = useTranslation();
  const { data, error, isPending, isFetching, refetch } = useHealth();

  const tone = isPending ? 'pending' : data?.status === 'UP' ? 'up' : 'down';
  const label = isPending
    ? t('health.checking')
    : tone === 'up'
      ? t('health.up')
      : t('health.down');

  return (
    <Page>
      <PageHeader>
        <PageTitle>{t('health.title')}</PageTitle>
        <PageSubtitle>{t('health.subtitle')}</PageSubtitle>
      </PageHeader>

      <StatusCard>
        <StatusRow>
          <Badge $tone={tone}>
            <Dot $tone={tone} aria-hidden="true" />
            <span role="status">{label}</span>
          </Badge>

          <Button
            size="small"
            icon={<ReloadOutlined />}
            loading={isFetching}
            onClick={() => {
              void refetch();
            }}
          >
            {t('health.retry')}
          </Button>
        </StatusRow>

        <Divider />

        <Details>
          <DetailList>
            <dt>{t('health.endpoint')}</dt>
            <dd>{`${import.meta.env.VITE_API_BASE_URL ?? ''}/actuator/health`}</dd>

            <dt>{t('health.status')}</dt>
            <dd>{data?.status ?? '—'}</dd>

            {data?.components && (
              <>
                <dt>{t('health.components')}</dt>
                <dd>
                  {Object.entries(data.components)
                    .map(([name, component]) => `${name}: ${component.status}`)
                    .join(' · ')}
                </dd>
              </>
            )}

            {isApiError(error) && (
              <>
                <dt>{t('health.errorCode')}</dt>
                <dd>{error.code}</dd>

                {error.correlationId !== undefined && (
                  <>
                    <dt>{t('health.correlationId')}</dt>
                    <dd>{error.correlationId}</dd>
                  </>
                )}
              </>
            )}
          </DetailList>

          {error !== null && <Hint>{t('health.hint')}</Hint>}
        </Details>
      </StatusCard>
    </Page>
  );
}
