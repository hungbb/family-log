import { DownOutlined, PlusOutlined, TeamOutlined } from '@ant-design/icons';
import { Dropdown, Space } from 'antd';
import { useTranslation } from 'react-i18next';
import { useNavigate, useParams } from 'react-router';

import { useActiveClan } from '../../hooks/useActiveClan';

import { SwitcherButton, SwitcherCode, SwitcherName } from './styles';

/**
 * Picks which family's records the shell is showing (F2.3).
 *
 * Lives in the shell rather than in the clan feature because the top bar renders it and
 * `components/` may not import from `features/` (doc 07 §2). It reads `useActiveClan`, which is in
 * the shared hooks layer for the same reason.
 *
 * Switching navigates as well as setting the store: every clan-scoped route carries the clan in its
 * path (doc 05 §1 — the clan in the path is authoritative), so changing the store alone would leave
 * the URL pointing at the previous family.
 */
export function ClanSwitcher() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const { clanId: routeClanId } = useParams();
  const { clan, clans, isLoading, setActiveClan } = useActiveClan();

  if (isLoading) {
    return null;
  }

  if (clans.length === 0) {
    return (
      <SwitcherButton
        type="text"
        icon={<PlusOutlined />}
        onClick={() => void navigate('/clans/new')}
      >
        {t('clan.switcher.createFirst')}
      </SwitcherButton>
    );
  }

  const onSelect = (nextClanId: string) => {
    setActiveClan(nextClanId);
    // Only rewrite the URL when it names a clan; from /me the switcher is a preference, not a
    // navigation, and yanking the person off their profile page would be rude.
    if (routeClanId !== undefined) {
      void navigate(`/clans/${nextClanId}/members`);
    }
  };

  return (
    <Dropdown
      trigger={['click']}
      menu={{
        selectedKeys: clan ? [clan.id] : [],
        items: [
          ...clans.map((option) => ({
            key: option.id,
            icon: <TeamOutlined />,
            label: option.name,
          })),
          { type: 'divider' as const },
          { key: 'new', icon: <PlusOutlined />, label: t('clan.switcher.create') },
          { key: 'join', icon: <TeamOutlined />, label: t('clan.switcher.join') },
        ],
        onClick: ({ key }) => {
          if (key === 'new') {
            void navigate('/clans/new');
          } else if (key === 'join') {
            void navigate('/join');
          } else {
            onSelect(key);
          }
        },
      }}
    >
      <SwitcherButton type="text" aria-label={t('clan.switcher.label')}>
        <Space size={8}>
          <SwitcherName>{clan?.name ?? t('clan.switcher.none')}</SwitcherName>
          {clan && <SwitcherCode>{clan.clanCode}</SwitcherCode>}
          <DownOutlined />
        </Space>
      </SwitcherButton>
    </Dropdown>
  );
}
