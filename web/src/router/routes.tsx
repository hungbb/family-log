import { createBrowserRouter, Navigate } from 'react-router';

import { RouteErrorBoundary } from '../components/feedback/RouteErrorBoundary';
import { AppShell } from '../components/layout/AppShell';
import { HealthPage } from '../features/health/pages/HealthPage';

/**
 * The route tree.
 *
 * Phase 0 has exactly one screen. The structure doc 07 §8 describes — `/login`, `/clans/:clanId`
 * and its children, the `RequireAuth` / `RequireClan` / `RequirePermission` guards — arrives with
 * the phases that give it something to guard. Building the guards now would mean writing four
 * components that all return `<Outlet />`.
 *
 * Later screens are lazily imported so route-level code splitting keeps the initial bundle inside
 * the 250 KB budget (doc 07 §10); `HealthPage` is eager because it is the entry point.
 */
export const router = createBrowserRouter([
  {
    path: '/',
    element: <AppShell />,
    errorElement: <RouteErrorBoundary />,
    children: [
      { index: true, element: <Navigate to="/health" replace /> },
      { path: 'health', element: <HealthPage /> },
      { path: '*', element: <Navigate to="/health" replace /> },
    ],
  },
]);
