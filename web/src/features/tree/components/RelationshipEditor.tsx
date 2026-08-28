import { App as AntApp, Button, Form, Select, Space } from 'antd';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';

import { type ParentRole, type PersonDetail, type UnionType } from '@/api/endpoints/persons.ts';
import { toMessage } from '@/lib/apiErrors.ts';

import {
  useAddParentage,
  useAttachChildToUnion,
  useCreateUnion,
  useRemoveParentage,
} from '../hooks/useTree';

import { PersonPicker } from './PersonPicker';
import { RelationGroup, RelationTitle } from './styles';

interface RelationshipEditorProps {
  clanId: string;
  person: PersonDetail;
}

/**
 * Editing the links around one person (F4.5).
 *
 * Three operations, and each one needs permission on <b>every</b> person it touches (doc 04 §5.2) —
 * so every picker here offers only people the caller may edit. That is ergonomics, not enforcement:
 * the server refuses an id that arrives anyway, and this only spares somebody the refusal.
 *
 * Removing a parent is offered next to the parent it removes rather than as a list of edges,
 * because "remove father" is what somebody means and "delete parentage 8f0e…" is not.
 */
export function RelationshipEditor({ clanId, person }: RelationshipEditorProps) {
  const { t } = useTranslation();
  const { message, modal } = AntApp.useApp();

  const addParentage = useAddParentage(clanId);
  const removeParentage = useRemoveParentage(clanId);
  const createUnion = useCreateUnion(clanId);
  const attachChild = useAttachChildToUnion(clanId);

  const [parentId, setParentId] = useState<string | undefined>(undefined);
  const [parentRole, setParentRole] = useState<ParentRole | undefined>(undefined);
  const [spouseId, setSpouseId] = useState<string | undefined>(undefined);
  const [unionType, setUnionType] = useState<UnionType>('PRIMARY');
  const [attachUnionId, setAttachUnionId] = useState<string | undefined>(undefined);
  const [attachChildId, setAttachChildId] = useState<string | undefined>(undefined);

  const relatives = [
    person.id,
    ...person.parents.map((parent) => parent.person.id),
    ...person.unions.map((link) => link.partner.id),
    ...person.children.map((child) => child.id),
  ];

  const run = async (action: () => Promise<unknown>, done: string) => {
    try {
      await action();
      void message.success(done);
    } catch (failure) {
      void message.error(toMessage(failure, t));
    }
  };

  return (
    <>
      <RelationGroup>
        <RelationTitle>{t('tree.editor.addParent')}</RelationTitle>
        <Form layout="vertical">
          <Form.Item label={t('tree.editor.parent')}>
            <PersonPicker
              clanId={clanId}
              editableOnly
              value={parentId}
              exclude={[person.id]}
              onChange={setParentId}
            />
          </Form.Item>
          <Form.Item label={t('tree.editor.role')} help={t('tree.editor.roleHelp')}>
            <Select
              allowClear
              value={parentRole}
              onChange={setParentRole}
              options={(['FATHER', 'MOTHER'] as ParentRole[]).map((value) => ({
                value,
                label: t(`tree.role.${value}`),
              }))}
            />
          </Form.Item>
          <Button
            type="primary"
            disabled={parentId === undefined}
            loading={addParentage.isPending}
            onClick={() => {
              if (parentId === undefined) return;
              void run(
                () =>
                  addParentage.mutateAsync({
                    parentPersonId: parentId,
                    childPersonId: person.id,
                    ...(parentRole === undefined ? {} : { parentRole }),
                  }),
                t('tree.editor.parentAdded'),
              );
              setParentId(undefined);
            }}
          >
            {t('tree.editor.link')}
          </Button>
        </Form>

        {person.parents.length > 0 && (
          <Space wrap>
            {person.parents.map((parent) => (
              <Button
                key={parent.parentageId}
                danger
                size="small"
                onClick={() => {
                  modal.confirm({
                    title: t('tree.editor.removeParentTitle', { name: parent.person.fullName }),
                    content: t('tree.editor.removeParentBody'),
                    okText: t('tree.editor.remove'),
                    cancelText: t('common.cancel'),
                    okButtonProps: { danger: true },
                    onOk: () =>
                      run(
                        () => removeParentage.mutateAsync(parent.parentageId),
                        t('tree.editor.parentRemoved'),
                      ),
                  });
                }}
              >
                {t('tree.editor.removeParent', { name: parent.person.fullName })}
              </Button>
            ))}
          </Space>
        )}
      </RelationGroup>

      <RelationGroup>
        <RelationTitle>{t('tree.editor.addUnion')}</RelationTitle>
        <Form layout="vertical">
          <Form.Item label={t('tree.editor.spouse')}>
            <PersonPicker
              clanId={clanId}
              editableOnly
              value={spouseId}
              exclude={[person.id, ...person.unions.map((link) => link.partner.id)]}
              onChange={setSpouseId}
            />
          </Form.Item>
          <Form.Item label={t('tree.editor.unionType')} help={t('tree.editor.unionTypeHelp')}>
            <Select
              value={unionType}
              onChange={setUnionType}
              options={(['PRIMARY', 'SECONDARY', 'SEQUENTIAL'] as UnionType[]).map((value) => ({
                value,
                label: t(`tree.unionType.${value}`),
              }))}
            />
          </Form.Item>
          <Button
            type="primary"
            disabled={spouseId === undefined}
            loading={createUnion.isPending}
            onClick={() => {
              if (spouseId === undefined) return;
              void run(
                () =>
                  createUnion.mutateAsync({
                    partnerAId: person.id,
                    partnerBId: spouseId,
                    unionType,
                  }),
                t('tree.editor.unionAdded'),
              );
              setSpouseId(undefined);
            }}
          >
            {t('tree.editor.record')}
          </Button>
        </Form>
      </RelationGroup>

      {person.unions.length > 0 && (
        <RelationGroup>
          <RelationTitle>{t('tree.editor.attachChild')}</RelationTitle>
          <Form layout="vertical">
            <Form.Item label={t('tree.editor.union')}>
              <Select
                value={attachUnionId}
                onChange={setAttachUnionId}
                options={person.unions.map((link) => ({
                  value: link.union.id,
                  label: t('tree.editor.unionOption', {
                    partner: link.partner.fullName,
                    type: t(`tree.unionType.${link.union.unionType}`),
                  }),
                }))}
              />
            </Form.Item>
            <Form.Item label={t('tree.editor.child')} help={t('tree.editor.attachChildHelp')}>
              <PersonPicker
                clanId={clanId}
                editableOnly
                value={attachChildId}
                exclude={relatives}
                onChange={setAttachChildId}
              />
            </Form.Item>
            <Button
              type="primary"
              disabled={attachUnionId === undefined || attachChildId === undefined}
              loading={attachChild.isPending}
              onClick={() => {
                if (attachUnionId === undefined || attachChildId === undefined) return;
                void run(
                  () =>
                    attachChild.mutateAsync({
                      unionId: attachUnionId,
                      personId: attachChildId,
                    }),
                  t('tree.editor.childAttached'),
                );
                setAttachChildId(undefined);
              }}
            >
              {t('tree.editor.attach')}
            </Button>
          </Form>
        </RelationGroup>
      )}
    </>
  );
}
