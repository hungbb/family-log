import { lazy, Suspense, type ReactNode } from 'react';
import { createBrowserRouter, Navigate } from 'react-router';

import { RouteErrorBoundary } from '../components/feedback/RouteErrorBoundary';
import { SplashScreen } from '../components/feedback/SplashScreen';
import { AppShell } from '../components/layout/AppShell';

import { ClanHomeRedirect } from './ClanHomeRedirect';
import { RequireAnonymous, RequireAuth, RequireClan } from './guards';

/**
 * The route tree of doc 07 §8, as far as Phase 3 fills it in.
 *
 * Three bands. The signed-out screens sit behind `RequireAnonymous`; the signed-in but clan-less
 * screens (onboarding, create, join, profile) sit behind `RequireAuth` inside the shell; and the
 * clan-scoped screens sit behind `RequireClan` as well, because the clan in the path is
 * authoritative (doc 05 §1) and a URL naming a clan the caller is not in must not render.
 *
 * `/` is the onboarding landing rather than a redirect: it decides for itself whether the person
 * has a clan to be sent into, which is a decision only it can make once the membership list has
 * loaded.
 *
 * Screens are lazily imported so route-level splitting keeps the initial bundle inside the 250 KB
 * budget (doc 07 §10) — the login screen should not ship the branch tree.
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
const OnboardingPage = lazy(async () => ({
  default: (await import('../features/clan')).OnboardingPage,
}));
const CreateClanPage = lazy(async () => ({
  default: (await import('../features/clan')).CreateClanPage,
}));
const JoinClanPage = lazy(async () => ({
  default: (await import('../features/clan')).JoinClanPage,
}));
const ClanSettingsPage = lazy(async () => ({
  default: (await import('../features/clan')).ClanSettingsPage,
}));
const BranchesPage = lazy(async () => ({
  default: (await import('../features/branch')).BranchesPage,
}));
const MembersPage = lazy(async () => ({
  default: (await import('../features/members')).MembersPage,
}));
const PermissionsPage = lazy(async () => ({
  default: (await import('../features/permissions')).PermissionsPage,
}));
const TreePage = lazy(async () => ({
  default: (await import('../features/tree')).TreePage,
}));
const PersonSearchPage = lazy(async () => ({
  default: (await import('../features/tree')).PersonSearchPage,
}));
const ClaimsPage = lazy(async () => ({
  default: (await import('../features/claims')).ClaimsPage,
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
          { index: true, element: withSuspense(<OnboardingPage />) },
          { path: 'clans/new', element: withSuspense(<CreateClanPage />) },
          { path: 'join', element: withSuspense(<JoinClanPage />) },
          { path: 'me', element: withSuspense(<ProfilePage />) },
          { path: 'health', element: withSuspense(<HealthPage />) },
          {
            path: 'clans/:clanId',
            element: <RequireClan />,
            children: [
              { index: true, element: <ClanHomeRedirect /> },
              { path: 'tree', element: withSuspense(<TreePage />) },
              { path: 'search', element: withSuspense(<PersonSearchPage />) },
              { path: 'members', element: withSuspense(<MembersPage />) },
              { path: 'branches', element: withSuspense(<BranchesPage />) },
              { path: 'permissions', element: withSuspense(<PermissionsPage />) },
              { path: 'settings', element: withSuspense(<ClanSettingsPage />) },
              { path: 'claims', element: withSuspense(<ClaimsPage />) },
              // Not a relative `<Navigate to="tree">`: on a catch-all that resolves against
              // the unmatched URL and redirects to itself forever.
              { path: '*', element: <ClanHomeRedirect /> },
            ],
          },
          { path: '*', element: <Navigate to="/" replace /> },
        ],
      },
    ],
  },
]);
