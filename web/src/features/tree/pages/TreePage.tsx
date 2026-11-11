import {
  ApartmentOutlined,
  PlusOutlined,
  UndoOutlined,
  UnorderedListOutlined,
} from '@ant-design/icons';
import { Alert, App as AntApp, Button, Dropdown, Segmented, Skeleton, Statistic } from 'antd';
import { useEffect, useMemo, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useParams } from 'react-router';

import { type PersonSummary, type PlacementType } from '@/api/endpoints/persons.ts';
import { FindYourselfBanner } from '@/features/claims';
import { useActiveClan } from '@/hooks/useActiveClan.ts';
import { useAuth } from '@/hooks/useAuth.ts';
import { toMessage } from '@/lib/apiErrors.ts';

import { type NodeAction } from '../components/canvas/PersonNode';
import { CanvasFrame, CanvasOverlay, OverlayBody, OverlayTitle } from '../components/canvas/styles';
import { TreeCanvas, type TreeCanvasHandle } from '../components/canvas/TreeCanvas';
import { TreeToolbar } from '../components/canvas/TreeToolbar';
import { PersonDrawer } from '../components/PersonDrawer';
import { PersonFormModal, type PersonFormMode } from '../components/PersonFormModal';
import {
  Page,
  PageHeader,
  PageSubtitle,
  PageTitle,
  PersonName,
  SectionCard,
  StatGrid,
} from '../components/styles';
import { TreeListView } from '../components/TreeListView';
import { useDeletedPersons, useRestorePerson, useTree, useTreeStats } from '../hooks/useTree';
import { useTreeLayout } from '../hooks/useTreeLayout';
import { useTreeViewStore } from '../stores/useTreeViewStore';

/**
 * The *gia phả*, as one tree (FR-T2, G6, M4).
 *
 * **One payload, two views.** The canvas and the list read the same `useTree` query, so they carry
 * the same nodes, the same `canEdit` flags and the same invalidation — a change made in one is
 * visible in the other without a refetch, and the two can never disagree about what the family
 * contains. The toggle is a preference, not a mode: NFR-7 makes the list the permanent accessible
 * route, and it is also the one that works on a phone.
 *
 * **Branch isolation is a visual fact here.** Every member sees the whole clan (doc 04 §5.5) — a
 * *gia phả* in fragments is not a *gia phả* — and the parts they may edit are the parts that carry
 * actions. Hovering somebody else's branch shows a card with no controls and a sentence saying which
 * branch they *can* edit, which is the same rule the server enforces, stated where somebody will
 * read it.
 */
export function TreePage() {
  const { t } = useTranslation();
  const { message } = AntApp.useApp();
  const { clanId = '' } = useParams();
  const { can } = useActiveClan();
  const { user } = useAuth();

  const { data: tree, isPending, error } = useTree(clanId);
  const { data: stats } = useTreeStats(clanId);

  const isAdmin = can('CLAN_ADMIN');
  const { data: deleted } = useDeletedPersons(clanId, isAdmin);
  const restorePerson = useRestorePerson(clanId);

  const mode = useTreeViewStore((state) => state.mode);
  const setMode = useTreeViewStore((state) => state.setMode);
  const openClan = useTreeViewStore((state) => state.openClan);
  const openPersonId = useTreeViewStore((state) => state.openPersonId);
  const openPerson = useTreeViewStore((state) => state.openPerson);

  const canvasRef = useRef<TreeCanvasHandle>(null);
  const [creating, setCreating] = useState<PersonFormMode | null>(null);

  // Panning into one family and then switching clans should not land somewhere arbitrary in
  // another's tree; the store drops its viewport and collapsed set when the clan changes.
  useEffect(() => {
    if (clanId !== '') openClan(clanId);
  }, [clanId, openClan]);

  const { graph, layout } = useTreeLayout(tree);

  /** The node linked to the signed-in account, if there is one — what "find me" jumps to. */
  const myPersonId = useMemo(
    () =>
      user === null
        ? undefined
        : tree?.persons.find((person) => person.linkedUserId === user.id)?.id,
    [tree, user],
  );

  const canAddAnywhere = can('TREE_EDIT');
  const isEmpty = !isPending && (tree?.persons.length ?? 0) === 0;

  const onNodeAction = (action: NodeAction, person: PersonSummary) => {
    if (action === 'add-child') {
      setCreating({ kind: 'create', placementType: 'CHILD_OF_PARENT', anchorPersonId: person.id });
      return;
    }
    if (action === 'add-spouse') {
      setCreating({ kind: 'create', placementType: 'SPOUSE_OF', anchorPersonId: person.id });
      return;
    }
    // Editing and removing both need the full record — the version to write against, the children
    // the confirmation has to name — so both open the drawer that already loads it.
    openPerson(person.id);
  };

  if (error !== null) {
    return (
      <Page>
        <PageHeader>
          <div>
            <PageTitle>{t('tree.title')}</PageTitle>
            <PageSubtitle>{toMessage(error, t)}</PageSubtitle>
          </div>
        </PageHeader>
      </Page>
    );
  }

  return (
    <Page>
      <PageHeader>
        <div>
          <PageTitle>{t('tree.title')}</PageTitle>
          <PageSubtitle>{t('tree.subtitle')}</PageSubtitle>
        </div>

        <div>
          <Segmented<'canvas' | 'list'>
            value={mode}
            onChange={setMode}
            options={[
              {
                value: 'canvas',
                icon: <ApartmentOutlined />,
                label: t('tree.view.canvas'),
              },
              {
                value: 'list',
                icon: <UnorderedListOutlined />,
                label: t('tree.view.list'),
              },
            ]}
          />
          {canAddAnywhere && (
            <Dropdown
              menu={{
                items: (
                  ['CHILD_OF_PARENT', 'SPOUSE_OF', 'BRANCH_ROOT', 'UNPLACED'] as PlacementType[]
                ).map((placementType) => ({
                  key: placementType,
                  label: t(`tree.placement.${placementType}`),
                  onClick: () => {
                    setCreating({ kind: 'create', placementType });
                  },
                })),
              }}
            >
              <Button type="primary" icon={<PlusOutlined />}>
                {t('tree.action.addPerson')}
              </Button>
            </Dropdown>
          )}
        </div>
      </PageHeader>

      {stats !== undefined && (
        <SectionCard>
          <StatGrid>
            <Statistic title={t('tree.stats.people')} value={stats.personCount} />
            <Statistic title={t('tree.stats.generations')} value={stats.maxGeneration ?? 0} />
            <Statistic title={t('tree.stats.living')} value={stats.livingCount} />
            <Statistic title={t('tree.stats.unlinked')} value={stats.unlinkedCount} />
          </StatGrid>
        </SectionCard>
      )}

      {tree?.meta.truncated === true && (
        <Alert type="info" showIcon message={t('tree.truncated')} />
      )}

      {/* F6.1. Shown only to a member who holds no node and has no claim waiting, so it stops
          appearing the moment it has been acted on. */}
      <FindYourselfBanner clanId={clanId} />

      {mode === 'canvas' ? (
        <>
          <TreeToolbar
            persons={tree?.persons ?? []}
            branches={tree?.branches ?? []}
            {...(myPersonId === undefined ? {} : { myPersonId })}
            disabled={isPending || isEmpty}
            onJumpToPerson={(personId) => {
              canvasRef.current?.jumpTo(personId);
            }}
            onFit={() => {
              canvasRef.current?.fit();
            }}
          />

          {graph !== null && !isEmpty ? (
            <TreeCanvas
              graph={graph}
              layout={layout}
              controlRef={canvasRef}
              {...(myPersonId === undefined ? {} : { myPersonId })}
              onOpenPerson={openPerson}
              onNodeAction={onNodeAction}
            />
          ) : (
            // The overlay is `position: absolute; inset: 0`, so it needs the relatively-positioned
            // frame around it. Rendered bare it would anchor to the viewport and cover the whole
            // shell, including the sidebar (#20).
            <CanvasFrame>
              <CanvasOverlay>
                {isPending ? (
                  <Skeleton active paragraph={{ rows: 4 }} />
                ) : (
                  <>
                    <OverlayTitle>{t('tree.empty')}</OverlayTitle>
                    <OverlayBody>{t('tree.emptyHelp')}</OverlayBody>
                    {canAddAnywhere && (
                      <Button
                        type="primary"
                        icon={<PlusOutlined />}
                        onClick={() => {
                          setCreating({ kind: 'create', placementType: 'BRANCH_ROOT' });
                        }}
                      >
                        {t('tree.emptyAction')}
                      </Button>
                    )}
                  </>
                )}
              </CanvasOverlay>
            </CanvasFrame>
          )}
        </>
      ) : (
        <SectionCard>
          {isEmpty && (
            <Alert
              type="info"
              showIcon
              message={t('tree.empty')}
              description={t('tree.emptyHelp')}
            />
          )}
          <TreeListView
            tree={tree}
            loading={isPending}
            branchNames={graph?.branchNames ?? new Map()}
            onOpenPerson={openPerson}
          />
        </SectionCard>
      )}

      {isAdmin && deleted !== undefined && deleted.length > 0 && (
        <SectionCard title={t('tree.deleted.title')}>
          <PageSubtitle>{t('tree.deleted.help')}</PageSubtitle>
          {deleted.map((person) => (
            <div key={person.id}>
              <PersonName $readOnly>{person.fullName}</PersonName>
              <Button
                type="link"
                icon={<UndoOutlined />}
                loading={restorePerson.isPending}
                onClick={() => {
                  void restorePerson
                    .mutateAsync(person.id)
                    .then(() => message.success(t('tree.deleted.restored')))
                    .catch((failure: unknown) => message.error(toMessage(failure, t)));
                }}
              >
                {t('tree.deleted.restore')}
              </Button>
            </div>
          ))}
        </SectionCard>
      )}

      <PersonDrawer
        clanId={clanId}
        personId={openPersonId}
        onClose={() => {
          openPerson(null);
        }}
        onOpenPerson={openPerson}
      />

      {creating !== null && (
        <PersonFormModal
          clanId={clanId}
          open
          mode={creating}
          onClose={() => {
            setCreating(null);
          }}
          onSaved={(personId) => {
            // The tree re-lays out around the new node; jumping to it is what keeps somebody's place
            // instead of leaving them looking at where the family used to be (F5.11, demo step 5).
            openPerson(personId);
            canvasRef.current?.jumpTo(personId);
          }}
        />
      )}
    </Page>
  );
}
