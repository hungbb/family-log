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
import { useLocation, useNavigate } from 'react-router';

import { useActiveClan } from '../../hooks/useActiveClan';

import { Brand, BrandMark, BrandName, SiderFooter, Sider } from './styles';

interface SidebarProps {
  collapsed: boolean;
}

/**
 * Navigation.
 *
 * Items are enabled as the phases that own them land. Members and Branches are real from Phase 2;
 * the tree, permissions and claims stay disabled rather than linking to screens that do not exist —
 * a dead end is worse than a greyed-out label.
 *
 * With no clan selected, every clan-scoped item is disabled: there is nothing for them to point at
 * until the person creates or joins one.
 */
export function Sidebar({ collapsed }: SidebarProps) {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const location = useLocation();
  const { clanId, can } = useActiveClan();

  const clanBase = clanId === null ? null : `/clans/${clanId}`;
  const selected = location.pathname.split('/').at(-1) ?? '';

  // No `theme="light"` on `Sider`: it is a styled component, and a `theme` prop replaces the design
  // tokens for every interpolation inside it. See the note on `Sider` in ./styles.
  return (
    <Sider collapsible collapsed={collapsed} trigger={null} width={240} collapsedWidth={64}>
      <Brand $collapsed={collapsed}>
        <BrandMark aria-hidden="true">G</BrandMark>
        {!collapsed && <BrandName>{t('app.name')}</BrandName>}
      </Brand>

      <Menu
        mode="inline"
        selectedKeys={[selected]}
        onClick={({ key }) => {
          if (clanBase !== null) {
            void navigate(`${clanBase}/${key}`);
          }
        }}
        items={[
          { key: 'tree', icon: <PartitionOutlined />, label: t('nav.tree'), disabled: true },
          {
            key: 'members',
            icon: <TeamOutlined />,
            label: t('nav.members'),
            disabled: clanBase === null,
          },
          {
            key: 'branches',
            icon: <ApartmentOutlined />,
            label: t('nav.branches'),
            disabled: clanBase === null,
          },
          {
            key: 'permissions',
            icon: <SafetyOutlined />,
            label: t('nav.permissions'),
            disabled: true,
          },
          { key: 'claims', icon: <IdcardOutlined />, label: t('nav.claims'), disabled: true },
          {
            key: 'settings',
            icon: <SettingOutlined />,
            label: t('nav.settings'),
            // Hiding it would be worse: a member who cannot edit the clan should still be able to
            // see that a settings page exists and who to ask. The server decides either way.
            disabled: clanBase === null || !can('CLAN_ADMIN'),
          },
        ]}
      />

      {!collapsed && <SiderFooter>{t('app.tagline')}</SiderFooter>}
    </Sider>
  );
}
