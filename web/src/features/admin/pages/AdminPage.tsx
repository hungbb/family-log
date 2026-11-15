import { Tabs } from 'antd';
import { useTranslation } from 'react-i18next';
import { useLocation, useNavigate } from 'react-router';

import { UsersPanel } from '../components/UsersPanel';

import { Page, PageSubtitle, PageTitle, SectionCard } from './styles';

const SECTIONS = ['users'] as const;
type Section = (typeof SECTIONS)[number];

/**
 * Platform administration, for super admins.
 *
 * One page with a tab per subject, each with its own URL (`/admin/users`), so a reload or a pasted
 * link opens the same tab. Users is the first; platform settings join as further tabs.
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
          items={[{ key: 'users', label: t('admin.tab.users'), children: <UsersPanel /> }]}
        />
      </SectionCard>
    </Page>
  );
}
