import {
  LogoutOutlined,
  MenuFoldOutlined,
  MenuUnfoldOutlined,
  UserOutlined,
} from '@ant-design/icons';
import { Avatar, Button, Dropdown, Segmented, Space } from 'antd';
import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router';

import { useAuth } from '../../hooks/useAuth';
import { SUPPORTED_LOCALES } from '../../i18n';

import { ClanSwitcher } from './ClanSwitcher';
import { AccountButton, TopBar as Bar, TopBarSpacer, UserName } from './styles';

interface TopBarProps {
  collapsed: boolean;
  onToggleSidebar: () => void;
}

/**
 * The top bar.
 *
 * The account menu is real from Phase 1: it shows who is signed in and offers the two things a
 * person wants from it — their profile, and the way out. The clan switcher is still a placeholder,
 * because there are no clans until Phase 2.
 */
export function AppTopBar({ collapsed, onToggleSidebar }: TopBarProps) {
  const { t, i18n } = useTranslation();
  const navigate = useNavigate();
  const { user, endSession } = useAuth();
  const currentLocale = SUPPORTED_LOCALES.find((locale) => i18n.language.startsWith(locale));

  return (
    <Bar>
      <Button
        type="text"
        icon={collapsed ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />}
        onClick={onToggleSidebar}
        aria-label={collapsed ? t('shell.expandSidebar') : t('shell.collapseSidebar')}
      />

      <ClanSwitcher />

      <TopBarSpacer />

      <Segmented
        size="small"
        aria-label={t('language.label')}
        value={currentLocale ?? SUPPORTED_LOCALES[0]}
        onChange={(value) => {
          void i18n.changeLanguage(value);
        }}
        options={SUPPORTED_LOCALES.map((locale) => ({
          value: locale,
          label: t(`language.${locale}`),
        }))}
      />

      <Dropdown
        trigger={['click']}
        menu={{
          items: [
            { key: 'profile', icon: <UserOutlined />, label: t('shell.profile') },
            { type: 'divider' },
            { key: 'signOut', icon: <LogoutOutlined />, label: t('shell.signOut'), danger: true },
          ],
          onClick: ({ key }) => {
            if (key === 'profile') {
              void navigate('/me');
            }
            if (key === 'signOut') {
              // The guard sends us to /login once the store goes anonymous; no navigation here.
              void endSession();
            }
          },
        }}
      >
        <AccountButton type="text" aria-label={t('shell.accountMenu')}>
          <Space size={8}>
            <Avatar size="small">{initialsOf(user?.displayName)}</Avatar>
            <UserName>{user?.displayName ?? ''}</UserName>
          </Space>
        </AccountButton>
      </Dropdown>
    </Bar>
  );
}

/**
 * Up to two initials from a display name.
 *
 * Takes the first and last words, which is what reads as a person's initials in both the Vietnamese
 * order (Nguyễn Văn An gives NA) and the English one. Falls back to an icon-free blank rather than
 * guessing when there is no name yet.
 */
function initialsOf(displayName: string | undefined): string {
  if (displayName === undefined || displayName.trim() === '') {
    return '';
  }
  const words = displayName.trim().split(/\s+/);
  const first = words[0] ?? '';
  const last = words.length > 1 ? (words[words.length - 1] ?? '') : '';
  return (first.charAt(0) + last.charAt(0)).toUpperCase();
}
