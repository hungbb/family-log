import { Button, Result } from 'antd';
import { useTranslation } from 'react-i18next';
import { isRouteErrorResponse, useRouteError } from 'react-router';

/**
 * Route-level error boundary (doc 09 §3.2).
 *
 * Placed on the shell route so a failure inside one screen renders here instead of blanking the
 * whole application — which matters most for the tree, where a single malformed node should not
 * take the navigation down with it.
 */
export function RouteErrorBoundary() {
  const { t } = useTranslation();
  const error = useRouteError();

  const detail = isRouteErrorResponse(error)
    ? `${error.status} ${error.statusText}`
    : error instanceof Error
      ? error.message
      : undefined;

  return (
    <Result
      status="warning"
      title={t('error.unexpectedTitle')}
      subTitle={detail ?? t('error.unexpectedBody')}
      extra={
        <Button
          type="primary"
          onClick={() => {
            window.location.reload();
          }}
        >
          {t('error.reload')}
        </Button>
      }
    />
  );
}
