import { Navigate, Outlet, useLocation } from 'react-router';

import { SplashScreen } from '../components/feedback/SplashScreen';
import { useAuth } from '../hooks/useAuth';

/**
 * Keeps signed-out visitors out of the application (task F1.5).
 *
 * **A convenience, never the security boundary** (doc 07 §8). Everything behind it is fetched from
 * an API that checks the token itself; this exists so a person sees a login form instead of a page
 * full of failed requests.
 *
 * The path they were heading for travels in the navigation state, so signing in returns them there
 * rather than to the dashboard — which matters most for the case this guard is really for: a
 * session that expired while someone had a page open.
 */
export function RequireAuth() {
  const { isAuthenticated, isResolving } = useAuth();
  const location = useLocation();

  if (isResolving) {
    // The bootstrap has not answered yet. Redirecting now would sign out every reload.
    return <SplashScreen />;
  }
  if (!isAuthenticated) {
    return <Navigate to="/login" replace state={{ from: location.pathname + location.search }} />;
  }
  return <Outlet />;
}

/**
 * The mirror image: keeps signed-in people off the login and register screens.
 *
 * Without it, the back button after signing in lands on a login form that would sign the same
 * person in again — confusing, and a needless second session.
 */
export function RequireAnonymous() {
  const { isAuthenticated, isResolving } = useAuth();

  if (isResolving) {
    return <SplashScreen />;
  }
  return isAuthenticated ? <Navigate to="/" replace /> : <Outlet />;
}
