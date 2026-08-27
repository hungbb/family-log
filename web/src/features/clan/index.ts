/**
 * The clan feature's public surface (doc 07 §2).
 *
 * Everything outside this folder imports from here, never from a file inside it — which is what
 * lets the folder be reorganised without a search-and-replace across the application.
 */
export { ClanSettingsPage } from './pages/ClanSettingsPage';
export { CreateClanPage } from './pages/CreateClanPage';
export { JoinClanPage } from './pages/JoinClanPage';
export { OnboardingPage } from './pages/OnboardingPage';
