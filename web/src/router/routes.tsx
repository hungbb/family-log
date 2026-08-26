import { lazy, Suspense, type ReactNode } from 'react';
import { createBrowserRouter, Navigate } from 'react-router';

import { RouteErrorBoundary } from '../components/feedback/RouteErrorBoundary';
import { SplashScreen } from '../components/feedback/SplashScreen';
import { AppShell } from '../components/layout/AppShell';

import { RequireAnonymous, RequireAuth } from './guards';

/**
 * The route tree of doc 07 §8, as far as Phase 1 fills it in.
 *
 * Two halves. The signed-out screens sit behind `RequireAnonymous`; everything else sits behind
 * `RequireAuth` and inside the shell. Clan-scoped routes (`/clans/:clanId/...`) arrive with Phase 2,
 * along with the `RequireClan` guard that owns them.
 *
 * Screens are lazily imported so route-level splitting keeps the initial bundle inside the 250 KB
 * budget (doc 07 §10) — the login screen should not ship the profile page, and neither should ship
 * the tree visualizer when it exists.
 */

const LoginPage = lazy(async () => ({ default: (await import('../features/auth')).LoginPage }));
const RegisterPage = lazy(async () => ({
  default: (await import('../features/auth')).RegisterPage,
}));
const ForgotPasswordPage = lazy(async () => ({
  default: (await import('../features/auth')).ForgotPasswordPage,
}));
const ResetPasswordPage = lazy(async () => ({
  default: (await import('../features/auth')).ResetPasswordPage,
}));
const VerifyEmailPage = lazy(async () => ({
  default: (await import('../features/auth')).VerifyEmailPage,
}));
const ProfilePage = lazy(async () => ({
  default: (await import('../features/account')).ProfilePage,
}));
const HealthPage = lazy(async () => ({
  default: (await import('../features/health/pages/HealthPage')).HealthPage,
}));

/** Every lazy route renders through the same fallback, so a slow chunk never blanks the page. */
function withSuspense(element: ReactNode): ReactNode {
  return <Suspense fallback={<SplashScreen />}>{element}</Suspense>;
}

export const router = createBrowserRouter([
  {
    element: <RequireAnonymous />,
    errorElement: <RouteErrorBoundary />,
    children: [
      { path: '/login', element: withSuspense(<LoginPage />) },
      { path: '/register', element: withSuspense(<RegisterPage />) },
      { path: '/forgot-password', element: withSuspense(<ForgotPasswordPage />) },
    ],
  },
  {
    // Reachable either way: a reset link is opened from an inbox, and the person following it may
    // well still have a live session in this browser.
    errorElement: <RouteErrorBoundary />,
    children: [
      { path: '/reset-password', element: withSuspense(<ResetPasswordPage />) },
      { path: '/verify-email', element: withSuspense(<VerifyEmailPage />) },
    ],
  },
  {
    element: <RequireAuth />,
    errorElement: <RouteErrorBoundary />,
    children: [
      {
        path: '/',
        element: <AppShell />,
        children: [
          { index: true, element: <Navigate to="/me" replace /> },
          { path: 'me', element: withSuspense(<ProfilePage />) },
          { path: 'health', element: withSuspense(<HealthPage />) },
          { path: '*', element: <Navigate to="/me" replace /> },
        ],
      },
    ],
  },
]);
