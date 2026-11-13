import {
  ApartmentOutlined,
  PlusOutlined,
  UndoOutlined,
  UnorderedListOutlined,
} from '@ant-design/icons';
import { Alert, App as AntApp, Button, Dropdown, Segmented, Skeleton, Statistic } from 'antd';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useParams, useSearchParams } from 'react-router';

import { type PersonSummary, type PlacementType } from '@/api/endpoints/persons.ts';
import { FindYourselfBanner } from '@/features/claims';
import { useActiveClan } from '@/hooks/useActiveClan.ts';
import { useAuth } from '@/hooks/useAuth.ts';
import { toMessage } from '@/lib/apiErrors.ts';
import { personIdIn, withPerson } from '@/router/paths.ts';

import { type NodeAction } from '../components/canvas/PersonNode';
import { PrintDialog } from '../components/canvas/PrintDialog';
import {
  CanvasFrame,
  CanvasOverlay,
  OverlayBody,
  OverlayTitle,
  TreeStage,
} from '../components/canvas/styles';
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
import { collapsedBeyond, levelCount } from '../model/depth';
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
  const { can, clan } = useActiveClan();
  const { user } = useAuth();

  const { data: tree, isPending, error } = useTree(clanId);
  const { data: stats } = useTreeStats(clanId);

  const isAdmin = can('CLAN_ADMIN');
  const { data: deleted } = useDeletedPersons(clanId, isAdmin);
  const restorePerson = useRestorePerson(clanId);

  const mode = useTreeViewStore((state) => state.mode);
  const setMode = useTreeViewStore((state) => state.setMode);
  const openClan = useTreeViewStore((state) => state.openClan);
  const fullscreen = useTreeViewStore((state) => state.fullscreen);
  const setFullscreen = useTreeViewStore((state) => state.setFullscreen);
  const scheme = useTreeViewStore((state) => state.scheme);
  const collapsed = useTreeViewStore((state) => state.collapsed);
  const [printOpen, setPrintOpen] = useState(false);

  useFullscreen(fullscreen && mode === 'canvas', setFullscreen);

  /**
   * The open person lives in the URL, not in the view store (FR-T5, #48).
   *
   * That is what makes a card shareable and a reload harmless, and what puts Back in charge of
   * closing the drawer. Opening pushes an entry; closing replaces it, so a drawer that has been
   * closed leaves nothing behind for Back to re-open.
   */
  const [searchParams, setSearchParams] = useSearchParams();
  const openPersonId = personIdIn(searchParams);
  const openPerson = useCallback(
    (personId: string | null) => {
      setSearchParams((current) => withPerson(current, personId), { replace: personId === null });
    },
    [setSearchParams],
  );

  const canvasRef = useRef<TreeCanvasHandle>(null);
  const [creating, setCreating] = useState<PersonFormMode | null>(null);

  // Panning into one family and then switching clans should not land somewhere arbitrary in
  // another's tree; the store drops its viewport and collapsed set when the clan changes.
  useEffect(() => {
    if (clanId !== '') openClan(clanId);
  }, [clanId, openClan]);

  const { graph, layout } = useTreeLayout(tree);

  // "Show N generations" (#60): rebuild the collapsed set from the graph, then fit once the new
  // layout is on screen — the effect runs after the canvas has re-rendered, so `fit` sees it.
  const limitGenerations = useTreeViewStore((state) => state.limitGenerations);
  const levels = useMemo(() => (graph === null ? 0 : levelCount(graph.roots)), [graph]);
  const fitPendingRef = useRef(false);
  const onLimitGenerations = useCallback(
    (limit: number | null) => {
      if (graph === null) return;
      limitGenerations(limit, limit === null ? new Set() : collapsedBeyond(graph.roots, limit));
      fitPendingRef.current = true;
    },
    [graph, limitGenerations],
  );
  useEffect(() => {
    if (!fitPendingRef.current) return;
    fitPendingRef.current = false;
    canvasRef.current?.fit();
  }, [layout]);

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
        <TreeStage $fullscreen={fullscreen}>
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
            levels={levels}
            onLimitGenerations={onLimitGenerations}
            onPrint={() => {
              setPrintOpen(true);
            }}
          />

          {graph !== null && !isEmpty ? (
            <TreeCanvas
              graph={graph}
              layout={layout}
              controlRef={canvasRef}
              {...(myPersonId === undefined ? {} : { myPersonId })}
              {...(openPersonId === null ? {} : { focusPersonId: openPersonId })}
              onOpenPerson={openPerson}
              onNodeAction={onNodeAction}
            />
          ) : (
            // The overlay is `position: absolute; inset: 0`, so it needs the relatively-positioned
            // frame around it. Rendered bare it would anchor to the viewport and cover the whole
            // shell, including the sidebar (#20).
            <CanvasFrame $scheme={scheme}>
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
        </TreeStage>
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
            {...(myPersonId === undefined ? {} : { myPersonId })}
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

      {graph !== null && (
        <PrintDialog
          open={printOpen}
          onClose={() => {
            setPrintOpen(false);
          }}
          graph={graph}
          collapsed={collapsed}
          title={clan?.name ?? t('tree.title')}
          {...(myPersonId === undefined ? {} : { myPersonId })}
        />
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

/**
 * Keeps the fullscreen layer and the browser's own fullscreen in step (#60).
 *
 * Entering asks for document fullscreen — the whole page, not the stage, so the drawer and the
 * dropdowns portalled to `<body>` stay visible — and leaving gives it back. Leaving the browser's
 * fullscreen by its own means (Esc, F11) leaves the layer too. Where the API is missing (iOS Safari)
 * the layer works on its own and Esc closes it. Page scroll is locked while it is up, and leaving
 * the page always leaves fullscreen, so the next visit does not open into it.
 */
function useFullscreen(active: boolean, setFullscreen: (fullscreen: boolean) => void) {
  useEffect(() => {
    if (!active) return;
    const root = document.documentElement;
    const native = typeof root.requestFullscreen === 'function' && document.fullscreenEnabled;
    let entered = false;

    if (native && document.fullscreenElement === null) {
      root
        .requestFullscreen()
        .then(() => {
          entered = true;
        })
        .catch(() => {
          // Refused (not from a user gesture, or blocked by policy): the layer still works.
        });
    }

    const onChange = () => {
      if (document.fullscreenElement === null && entered) setFullscreen(false);
    };
    const onKey = (event: KeyboardEvent) => {
      // With native fullscreen the browser owns Esc and `onChange` handles it.
      // An Esc that closes a drawer, a form or a dropdown is not also a request to leave fullscreen.
      const inPopup =
        event.target instanceof Element &&
        event.target.closest('.ant-drawer, .ant-modal-wrap, .ant-select-dropdown') !== null;
      if (event.key === 'Escape' && !entered && !inPopup) setFullscreen(false);
    };
    const overflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    document.addEventListener('fullscreenchange', onChange);
    document.addEventListener('keydown', onKey);

    return () => {
      document.removeEventListener('fullscreenchange', onChange);
      document.removeEventListener('keydown', onKey);
      document.body.style.overflow = overflow;
      if (document.fullscreenElement !== null) {
        document.exitFullscreen().catch(() => {
          // Already left by the browser's own means; nothing to undo.
        });
      }
    };
  }, [active, setFullscreen]);

  useEffect(
    () => () => {
      setFullscreen(false);
    },
    [setFullscreen],
  );
}
