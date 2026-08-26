import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { App as AntApp, ConfigProvider } from 'antd';
import enUS from 'antd/locale/en_US';
import viVN from 'antd/locale/vi_VN';
import dayjs from 'dayjs';
import 'dayjs/locale/vi';
import { I18nextProvider, useTranslation } from 'react-i18next';
import { RouterProvider } from 'react-router';
import { ThemeProvider } from 'styled-components';

import { ApiError } from './api/errors';
import { SplashScreen } from './components/feedback/SplashScreen';
import { useSessionBootstrap } from './hooks/useSessionBootstrap';
import i18n from './i18n';
import { router } from './router/routes';
import { antdTheme } from './theme/antdTheme';
import { GlobalStyle } from './theme/GlobalStyle';
import { tokens } from './theme/tokens';

/**
 * One client for the application's lifetime. Creating it inside the component would throw the
 * cache away on every render.
 */
const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 30_000,
      refetchOnWindowFocus: false,
      retry: (failureCount, error) => {
        // Retrying a 403 or a 422 cannot help — the answer will not change. Only transport
        // failures and server-side faults are worth a second attempt.
        if (error instanceof ApiError && !error.isRetryable) return false;
        return failureCount < 2;
      },
    },
    mutations: {
      // A mutation that failed may have partly succeeded; retrying is the caller's decision.
      retry: false,
    },
  },
});

/** Keeps AntD's own locale and dayjs in step with i18next, so dates and empty states match. */
function LocalisedProviders({ children }: { children: React.ReactNode }) {
  const { i18n: instance } = useTranslation();
  const isVietnamese = instance.language.startsWith('vi');
  dayjs.locale(isVietnamese ? 'vi' : 'en');

  return (
    <ConfigProvider theme={antdTheme} locale={isVietnamese ? viVN : enUS}>
      <ThemeProvider theme={tokens}>
        <GlobalStyle />
        <AntApp>{children}</AntApp>
      </ThemeProvider>
    </ConfigProvider>
  );
}

/**
 * Resolves the session before the router mounts (task F1.8).
 *
 * Inside the providers rather than around them, so the splash it shows is themed and translated —
 * and so the router never renders against a session that is still `loading`, which is what would
 * otherwise flash the login screen on every reload of a signed-in page.
 */
function RoutedApplication() {
  const resolved = useSessionBootstrap();

  return resolved ? <RouterProvider router={router} /> : <SplashScreen />;
}

/**
 * Provider order matters and is fixed by doc 07 §F0.4:
 * QueryClient → ConfigProvider → ThemeProvider → i18n → Router.
 *
 * i18n wraps the theme providers because `LocalisedProviders` reads the active language to pick
 * AntD's locale bundle; the router is innermost so every screen it renders sits inside all of them.
 */
export function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <I18nextProvider i18n={i18n}>
        <LocalisedProviders>
          <RoutedApplication />
        </LocalisedProviders>
      </I18nextProvider>
    </QueryClientProvider>
  );
}
