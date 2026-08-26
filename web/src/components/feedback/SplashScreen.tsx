import { Spin } from 'antd';
import { useTranslation } from 'react-i18next';
import styled from 'styled-components';

/**
 * What the application shows while the session is being resolved (task F1.8).
 *
 * It exists so that reloading a signed-in page does not flash the login screen for the length of
 * one refresh round trip. Deliberately almost empty: a splash that tried to look like the page it
 * is standing in for would be a second layout to maintain, and a wrong guess is more jarring than
 * a spinner.
 */
export function SplashScreen() {
  const { t } = useTranslation();

  return (
    <Centre>
      <Spin size="large" aria-label={t('common.loading')} />
    </Centre>
  );
}

const Centre = styled.div`
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  background: ${({ theme }) => theme.color.surfaceAlt};
`;
