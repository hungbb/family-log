import { MenuFoldOutlined, MenuUnfoldOutlined, UserOutlined } from '@ant-design/icons';
import { Avatar, Button, Segmented } from 'antd';
import { useTranslation } from 'react-i18next';

import { SUPPORTED_LOCALES } from '../../i18n';

import { Placeholder, TopBar as Bar, TopBarSpacer } from './styles';

interface TopBarProps {
  collapsed: boolean;
  onToggleSidebar: () => void;
}

/**
 * The top bar.
 *
 * The clan switcher and the account menu are placeholders — there are no clans and no accounts
 * until Phases 2 and 1. The language switch is real, because i18n is real from day one and it is
 * the cheapest way to keep the `en` stub honest.
 */
export function AppTopBar({ collapsed, onToggleSidebar }: TopBarProps) {
  const { t, i18n } = useTranslation();
  const currentLocale = SUPPORTED_LOCALES.find((locale) => i18n.language.startsWith(locale));

  return (
    <Bar>
      <Button
        type="text"
        icon={collapsed ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />}
        onClick={onToggleSidebar}
        aria-label={collapsed ? t('shell.expandSidebar') : t('shell.collapseSidebar')}
      />

      <Placeholder>{t('shell.clanSwitcherPlaceholder')}</Placeholder>

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

      <Avatar size="small" icon={<UserOutlined />} aria-label={t('shell.accountPlaceholder')} />
    </Bar>
  );
}
