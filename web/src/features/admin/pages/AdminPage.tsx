import { Tabs } from 'antd';
import { useTranslation } from 'react-i18next';
import { useLocation, useNavigate } from 'react-router';

import { MailSettingsPanel } from '../components/MailSettingsPanel';
import { UsersPanel } from '../components/UsersPanel';

import { Page, PageSubtitle, PageTitle, SectionCard } from './styles';

const SECTIONS = ['users', 'mail'] as const;
type Section = (typeof SECTIONS)[number];

/**
 * Platform administration, for super admins.
 *
 * One page with a tab per subject. Each tab has its own URL (`/admin/users`, `/admin/mail`), so a
 * reload or a pasted link opens the same tab. More settings are expected to join mail here as
 * further tabs.
 */
export function AdminPage() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const location = useLocation();

  const last = location.pathname.split('/').at(-1) ?? '';
  const active: Section = (SECTIONS as readonly string[]).includes(last)
    ? (last as Section)
    : 'users';

  return (
    <Page>
      <div>
        <PageTitle>{t('admin.title')}</PageTitle>
        <PageSubtitle>{t('admin.subtitle')}</PageSubtitle>
      </div>

      <SectionCard>
        <Tabs
          activeKey={active}
          onChange={(key) => {
            void navigate(`/admin/${key}`);
          }}
          items={[
            { key: 'users', label: t('admin.tab.users'), children: <UsersPanel /> },
            { key: 'mail', label: t('admin.tab.mail'), children: <MailSettingsPanel /> },
          ]}
        />
      </SectionCard>
    </Page>
  );
}
