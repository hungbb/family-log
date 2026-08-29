import { Alert, Button } from 'antd';
import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router';

import { useMyClaims, useMyLinkedNode } from '../hooks/useClaims';

interface FindYourselfBannerProps {
  clanId: string;
}

/**
 * "Find yourself in the tree" (F6.1).
 *
 * Shown to a member who holds no node in this clan and has no claim waiting — the whole of the
 * onboarding nudge, and deliberately a dismissible-looking info bar rather than a modal: somebody
 * who has just been admitted to a family record is here to look at it, not to fill in a form.
 *
 * It disappears the moment a claim is pending, because a second prompt to do the thing you have
 * already done reads as the first one having failed.
 */
export function FindYourselfBanner({ clanId }: FindYourselfBannerProps) {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const { data: myNode, isPending } = useMyLinkedNode(clanId);
  const { data: myClaims } = useMyClaims();

  const waiting = (myClaims ?? []).some(
    (claim) => claim.clanId === clanId && claim.status === 'PENDING',
  );

  if (isPending || myNode !== null || waiting) {
    return null;
  }

  return (
    <Alert
      type="info"
      showIcon
      message={t('claims.banner.title')}
      description={t('claims.banner.body')}
      action={
        <Button
          size="small"
          type="primary"
          onClick={() => {
            void navigate(`/clans/${clanId}/claims`);
          }}
        >
          {t('claims.banner.action')}
        </Button>
      }
    />
  );
}
