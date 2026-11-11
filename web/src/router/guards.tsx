import { Result } from 'antd';
import { useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { Navigate, Outlet, useLocation, useParams } from 'react-router';

import { SplashScreen } from '../components/feedback/SplashScreen';
import { useActiveClan } from '../hooks/useActiveClan';
import { useAuth } from '../hooks/useAuth';
import { returnPathOrHome } from '../lib/returnPath';

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
 *
 * **It sends them where they were going, not always home.** This guard is also the first thing to
 * react when somebody signs in: the session starts, this re-renders, and its redirect runs. It used
 * to say `/` unconditionally, which overrode the login form's own navigation to the stashed path —
 * so a deep link opened while signed out, or a page somebody had open when their session expired,
 * came back as the clan's default page. Both now read the same state through `returnPathOrHome`.
 */
export function RequireAnonymous() {
  const { isAuthenticated, isResolving } = useAuth();
  const location = useLocation();

  if (isResolving) {
    return <SplashScreen />;
  }
  return isAuthenticated ? <Navigate to={returnPathOrHome(location.state)} replace /> : <Outlet />;
}

/**
 * Guards the clan-scoped routes (F2.10).
 *
 * Checks the clan in the *path* rather than the one in the store, because doc 05 §1 makes the path
 * authoritative — a bookmarked or shared URL must resolve to the clan it names, not to whichever
 * one this browser last remembered.
 *
 * Three outcomes:
 *
 * - the caller is a member → render, and remember the clan for next time;
 * - they are not → the "not found or you are not a member" result, which is deliberately the same
 *   answer the API gives (doc 04 §7): a private clan does not confirm it exists;
 * - they belong to no clan at all → onboarding, because there is nothing here for them yet.
 *
 * Like every guard, this is a convenience. The API refuses the same request on its own.
 */
export function RequireClan() {
  const { t } = useTranslation();
  const { clanId } = useParams();
  const { clans, isLoading, hasNoClans, setActiveClan } = useActiveClan();

  const membership = clans.find((clan) => clan.id === clanId);

  // Following a link into a clan is a deliberate switch, so the switcher should agree with the URL.
  // In an effect rather than in the render body: writing to the store during render would update
  // every component subscribed to it mid-render, which React rightly refuses.
  useEffect(() => {
    if (membership !== undefined && clanId !== undefined) {
      setActiveClan(clanId);
    }
  }, [membership, clanId, setActiveClan]);

  if (isLoading) {
    return <SplashScreen />;
  }
  if (hasNoClans) {
    return <Navigate to="/" replace />;
  }
  if (membership === undefined) {
    return (
      <Result status="404" title={t('clan.notFound.title')} subTitle={t('clan.notFound.body')} />
    );
  }

  return <Outlet />;
}
