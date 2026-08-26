import { Button, Result, Spin } from 'antd';
import { useEffect, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Link, useSearchParams } from 'react-router';

import { verifyEmail } from '../../../api/endpoints/auth';
import { toMessage } from '../../../lib/apiErrors';
import { AuthLayout } from '../components/AuthLayout';

type Outcome = 'verifying' | 'verified' | 'failed';

/**
 * Confirms an address from a verification link (FR-A6).
 *
 * The link is opened, not submitted, so the work happens on mount. The token is single-use, which
 * makes a double submission a real failure rather than a wasted request: React's StrictMode runs
 * effects twice in development, and without the ref below the second run would spend the token the
 * first had already used and report a perfectly good link as invalid.
 *
 * A missing token is decided during render rather than in the effect. It is a property of the URL,
 * not something that happens; putting it in state would mean a render pass that shows a spinner
 * for a link that was never going to work.
 */
export function VerifyEmailPage() {
  const { t } = useTranslation();
  const [searchParams] = useSearchParams();
  const [outcome, setOutcome] = useState<Outcome>('verifying');
  const [failure, setFailure] = useState<string | null>(null);
  const attempted = useRef(false);

  const token = searchParams.get('token');

  useEffect(() => {
    if (token === null || attempted.current) {
      return;
    }
    attempted.current = true;

    verifyEmail(token)
      .then(() => {
        setOutcome('verified');
      })
      .catch((error: unknown) => {
        setFailure(toMessage(error, t));
        setOutcome('failed');
      });
  }, [token, t]);

  const state: Outcome = token === null ? 'failed' : outcome;
  const detail = token === null ? t('auth.verify.missingToken') : failure;

  return (
    <AuthLayout title={t('auth.verify.title')}>
      {state === 'verifying' && <Spin aria-label={t('auth.verify.working')} />}

      {state === 'verified' && (
        <Result
          status="success"
          title={t('auth.verify.successTitle')}
          subTitle={t('auth.verify.successBody')}
          extra={
            <Link to="/login">
              <Button type="primary">{t('auth.verify.continue')}</Button>
            </Link>
          }
        />
      )}

      {state === 'failed' && (
        <Result
          status="warning"
          title={t('auth.verify.failedTitle')}
          subTitle={detail ?? t('auth.verify.failedBody')}
          extra={
            <Link to="/login">
              <Button type="primary">{t('auth.verify.continue')}</Button>
            </Link>
          }
        />
      )}
    </AuthLayout>
  );
}
