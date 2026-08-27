import { DeleteOutlined, EditOutlined, PlusOutlined } from '@ant-design/icons';
import { App as AntApp, Alert, Button, Form, Input, Modal, Skeleton, Tag } from 'antd';
import { useMemo, useState, type Key, type ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import { useParams } from 'react-router';

import { flattenBranches, type Branch } from '@/api/endpoints/branches.ts';
import { useActiveClan } from '@/hooks/useActiveClan.ts';
import { toMessage } from '@/lib/apiErrors.ts';

import {
  useArchiveBranch,
  useBranches,
  useCreateBranch,
  useMoveBranch,
  useUpdateBranch,
} from '../hooks/useBranches';

import {
  BranchCodeTag,
  BranchMeta,
  BranchName,
  BranchRow,
  BranchTree,
  Page,
  PageHeader,
  PageSubtitle,
  PageTitle,
  RowActions,
  SectionCard,
} from './styles';

interface BranchFormValues {
  name: string;
  description?: string;
}

/** What the editor dialog is currently doing. */
type Editing = { mode: 'create'; parent: Branch } | { mode: 'rename'; branch: Branch } | null;

/**
 * Branch management (F2.5).
 *
 * An AntD `Tree` with drag-to-move. Dropping a branch onto another calls the move endpoint, which
 * rewrites the whole subtree's paths server-side — the client simply refetches, because reproducing
 * that rule here would mean maintaining two copies of it.
 *
 * **Illegal drops are disabled, not merely refused.** `draggable` says no to the root branch, and
 * `allowDrop` says no to a branch's own descendants and to archived parents. The server refuses all
 * three as well; this is the convenience layer, and doc 07 §8 is explicit that it never becomes the
 * boundary.
 */
export function BranchesPage() {
  const { t } = useTranslation();
  const { message, modal } = AntApp.useApp();
  const { clanId = '' } = useParams();
  const { can } = useActiveClan();
  const { data: branches, isPending, error } = useBranches(clanId);

  const createBranch = useCreateBranch(clanId);
  const updateBranch = useUpdateBranch(clanId);
  const moveBranch = useMoveBranch(clanId);
  const archiveBranch = useArchiveBranch(clanId);

  const [editing, setEditing] = useState<Editing>(null);
  const [form] = Form.useForm<BranchFormValues>();

  const canAdminister = can('CLAN_ADMIN');
  const rootBranch = branches?.[0];
  const flat = useMemo(() => flattenBranches(branches ?? []), [branches]);
  const byKey = useMemo(() => new Map(flat.map((branch) => [branch.id, branch])), [flat]);

  const openCreate = (parent: Branch) => {
    form.resetFields();
    setEditing({ mode: 'create', parent });
  };

  const openRename = (branch: Branch) => {
    form.setFieldsValue({
      name: branch.name,
      ...(branch.description !== undefined ? { description: branch.description } : {}),
    });
    setEditing({ mode: 'rename', branch });
  };

  const onSubmitDialog = async () => {
    const values = await form.validateFields();
    if (editing === null) return;
    try {
      if (editing.mode === 'create') {
        await createBranch.mutateAsync({
          parentBranchId: editing.parent.id,
          name: values.name,
          ...(values.description !== undefined ? { description: values.description } : {}),
        });
      } else {
        await updateBranch.mutateAsync({
          branchId: editing.branch.id,
          input: {
            name: values.name,
            ...(values.description !== undefined ? { description: values.description } : {}),
            version: editing.branch.version,
          },
        });
      }
      setEditing(null);
    } catch (failure) {
      void message.error(toMessage(failure, t));
    }
  };

  const onArchive = (branch: Branch) => {
    modal.confirm({
      title: t('branch.archive.confirmTitle', { name: branch.name }),
      content: t('branch.archive.confirmBody'),
      okText: t('branch.archive.confirmOk'),
      cancelText: t('common.cancel'),
      okButtonProps: { danger: true },
      onOk: async () => {
        try {
          await archiveBranch.mutateAsync(branch.id);
          void message.success(t('branch.archive.done'));
        } catch (failure) {
          void message.error(toMessage(failure, t));
        }
      },
    });
  };

  const onDrop = async (info: {
    node: { key: Key };
    dragNode: { key: Key };
    dropToGap: boolean;
  }) => {
    // Only dropping *onto* a node means anything here: the tree is a hierarchy, not an ordered
    // list, and siblings have no order the product cares about yet.
    if (info.dropToGap) return;
    const target = byKey.get(String(info.node.key));
    const dragged = byKey.get(String(info.dragNode.key));
    if (target === undefined || dragged === undefined) return;

    try {
      await moveBranch.mutateAsync({ branchId: dragged.id, newParentBranchId: target.id });
      void message.success(t('branch.move.done', { name: dragged.name, parent: target.name }));
    } catch (failure) {
      void message.error(toMessage(failure, t));
    }
  };

  const renderTitle = (branch: Branch) => (
    <BranchRow>
      <BranchName $archived={branch.archived}>{branch.name}</BranchName>
      <BranchCodeTag>{branch.branchCode}</BranchCodeTag>
      <BranchMeta>{t('branch.memberCount', { count: branch.memberCount })}</BranchMeta>
      {branch.archived && <Tag>{t('branch.archived')}</Tag>}
      {canAdminister && !branch.archived && (
        <RowActions>
          <Button
            type="text"
            size="small"
            icon={<PlusOutlined />}
            aria-label={t('branch.addChild')}
            onClick={() => {
              openCreate(branch);
            }}
          />
          <Button
            type="text"
            size="small"
            icon={<EditOutlined />}
            aria-label={t('branch.rename')}
            onClick={() => {
              openRename(branch);
            }}
          />
          {branch.parentBranchId !== undefined && (
            <Button
              type="text"
              size="small"
              danger
              icon={<DeleteOutlined />}
              aria-label={t('branch.archive.action')}
              onClick={() => {
                onArchive(branch);
              }}
            />
          )}
        </RowActions>
      )}
    </BranchRow>
  );

  const toTreeData = (nodes: Branch[]): TreeNode[] =>
    nodes.map((branch) => ({
      key: branch.id,
      title: renderTitle(branch),
      // The root branch is the clan itself and has nowhere to go (I-C2).
      disabled: false,
      selectable: false,
      children: toTreeData(branch.children),
    }));

  if (error) {
    return (
      <Page>
        <PageHeader>
          <div>
            <PageTitle>{t('branch.title')}</PageTitle>
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
          <PageTitle>{t('branch.title')}</PageTitle>
          <PageSubtitle>{t('branch.subtitle')}</PageSubtitle>
        </div>
        {canAdminister && rootBranch !== undefined && (
          <Button
            type="primary"
            icon={<PlusOutlined />}
            onClick={() => {
              openCreate(rootBranch);
            }}
          >
            {t('branch.add')}
          </Button>
        )}
      </PageHeader>

      {!isPending && !canAdminister && (
        <Alert type="info" showIcon message={t('branch.readOnly')} />
      )}

      <SectionCard>
        <Skeleton loading={isPending} active>
          <BranchTree
            showLine
            defaultExpandAll
            blockNode
            draggable={
              canAdminister
                ? {
                    icon: false,
                    // The root branch is the clan; it has nowhere to move to (I-C2).
                    nodeDraggable: (node) =>
                      byKey.get(String(node.key))?.parentBranchId !== undefined,
                  }
                : false
            }
            allowDrop={({ dropNode, dragNode, dropPosition }) => {
              if (dropPosition !== 0) return false;
              const target = byKey.get(nodeKey(dropNode));
              const dragged = byKey.get(nodeKey(dragNode));
              if (target === undefined || dragged === undefined) return false;
              // Nothing goes under an archived branch, and nothing goes under its own descendant —
              // the second is a cycle, which the server answers with 422 CYCLE_DETECTED.
              if (target.archived) return false;
              return !target.path.startsWith(dragged.path);
            }}
            onDrop={(info) => void onDrop(info)}
            treeData={toTreeData(branches ?? [])}
          />
        </Skeleton>
      </SectionCard>

      <Modal
        open={editing !== null}
        title={
          editing?.mode === 'create'
            ? t('branch.create.title', { parent: editing.parent.name })
            : t('branch.rename')
        }
        okText={t('common.save')}
        cancelText={t('common.cancel')}
        confirmLoading={createBranch.isPending || updateBranch.isPending}
        onOk={() => void onSubmitDialog()}
        onCancel={() => {
          setEditing(null);
        }}
      >
        <Form form={form} layout="vertical" requiredMark={false}>
          <Form.Item
            name="name"
            label={t('branch.field.name')}
            rules={[{ required: true, message: t('branch.validation.nameRequired') }]}
          >
            <Input placeholder={t('branch.placeholder.name')} maxLength={200} />
          </Form.Item>
          <Form.Item name="description" label={t('branch.field.description')}>
            <Input.TextArea rows={3} maxLength={2000} showCount />
          </Form.Item>
        </Form>
      </Modal>
    </Page>
  );
}

/** The shape AntD's `Tree` reads. Declared locally so the page owns its own view model. */
interface TreeNode {
  key: string;
  title: ReactNode;
  disabled: boolean;
  selectable: boolean;
  children: TreeNode[];
}

/**
 * A tree node's key, as a string.
 *
 * AntD types a node as `BasicDataNode | DataNode`, and only the second carries a key — the first is
 * the escape hatch for callers who supply their own node shape. Every node here comes from
 * `toTreeData` and has one, so the optional property is a type-level formality rather than a case
 * that happens.
 */
function nodeKey(node: object): string {
  return 'key' in node ? String(node.key) : '';
}
