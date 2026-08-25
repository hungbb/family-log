import {
  ApartmentOutlined,
  IdcardOutlined,
  PartitionOutlined,
  SafetyOutlined,
  SettingOutlined,
  TeamOutlined,
} from '@ant-design/icons';
import { Menu } from 'antd';
import { useTranslation } from 'react-i18next';

import { Brand, BrandMark, BrandName, SiderFooter, Sider } from './styles';

interface SidebarProps {
  collapsed: boolean;
}

/**
 * Navigation placeholder.
 *
 * The items are the routes doc 07 §8 lays out, rendered but inert: Phase 0 has one page, and
 * wiring links to screens that do not exist yet would only produce dead ends. Later phases replace
 * `disabled` with a `NavLink` and a `PermissionGate`.
 */
export function Sidebar({ collapsed }: SidebarProps) {
  const { t } = useTranslation();

  return (
    <Sider
      collapsible
      collapsed={collapsed}
      trigger={null}
      width={240}
      collapsedWidth={64}
      theme="light"
    >
      <Brand $collapsed={collapsed}>
        <BrandMark aria-hidden="true">G</BrandMark>
        {!collapsed && <BrandName>{t('app.name')}</BrandName>}
      </Brand>

      <Menu
        mode="inline"
        selectedKeys={['tree']}
        items={[
          { key: 'tree', icon: <PartitionOutlined />, label: t('nav.tree'), disabled: true },
          { key: 'members', icon: <TeamOutlined />, label: t('nav.members'), disabled: true },
          {
            key: 'branches',
            icon: <ApartmentOutlined />,
            label: t('nav.branches'),
            disabled: true,
          },
          {
            key: 'permissions',
            icon: <SafetyOutlined />,
            label: t('nav.permissions'),
            disabled: true,
          },
          { key: 'claims', icon: <IdcardOutlined />, label: t('nav.claims'), disabled: true },
          { key: 'settings', icon: <SettingOutlined />, label: t('nav.settings'), disabled: true },
        ]}
      />

      {!collapsed && <SiderFooter>{t('app.tagline')}</SiderFooter>}
    </Sider>
  );
}
