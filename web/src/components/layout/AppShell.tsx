import { Layout } from 'antd';
import { useState } from 'react';
import { Outlet } from 'react-router';

import { Sidebar } from './Sidebar';
import { Content, Shell } from './styles';
import { AppTopBar } from './TopBar';

/**
 * The application chrome: sidebar, top bar, and a content area the router fills.
 *
 * Built from Ant Design's `Layout` primitives, styled entirely with styled-components (doc 07 §3.2)
 * — there is no CSS file for this component and there will not be one.
 *
 * Sidebar collapse is local state on purpose. Doc 07 §6 puts anything that must survive a route
 * change in `useUiStore`; this does not, so a `useState` is the honest amount of machinery.
 */
export function AppShell() {
  const [collapsed, setCollapsed] = useState(false);

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
        <Content>
          <Outlet />
        </Content>
      </Layout>
    </Shell>
  );
}
