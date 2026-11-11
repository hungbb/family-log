import { Layout } from 'antd';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Outlet } from 'react-router';

import { useAuth } from '../../hooks/useAuth';

import { Sidebar } from './Sidebar';
import { Content, Shell, VerificationBanner } from './styles';
import { AppTopBar } from './TopBar';
import { sidebarStartsCollapsed } from './viewport';

/**
 * The application chrome: sidebar, top bar, and a content area the router fills.
 *
 * Built from Ant Design's `Layout` primitives, styled entirely with styled-components (doc 07 §3.2)
 * — there is no CSS file for this component and there will not be one.
 *
 * Sidebar collapse is local state on purpose. Doc 07 §6 puts anything that must survive a route
 * change in `useUiStore`; this does not, so a `useState` is the honest amount of machinery. Its
 * initial value depends on the viewport (see `viewport.ts`): a 240 px sidebar on a 390 px phone
 * leaves no page to look at.
 */
export function AppShell() {
  const { t } = useTranslation();
  // Open on a desktop, the icon rail on a phone — decided once, when the shell mounts. After that
  // the toggle is the person's, and a rotation or a resize does not overrule them.
  const [collapsed, setCollapsed] = useState(() => sidebarStartsCollapsed(window.innerWidth));
  const { user } = useAuth();

  const needsVerification = user !== null && !user.emailVerified;

  return (
    <Shell hasSider>
      <Sidebar collapsed={collapsed} />
      <Layout>
        <AppTopBar
          collapsed={collapsed}
          onToggleSidebar={() => {
            setCollapsed((previous) => !previous);
          }}
        />
        {needsVerification && (
          <VerificationBanner role="status">{t('shell.verifyEmailBanner')}</VerificationBanner>
        )}
        <Content>
          <Outlet />
        </Content>
      </Layout>
    </Shell>
  );
}
