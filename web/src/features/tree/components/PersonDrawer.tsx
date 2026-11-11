import { DeleteOutlined, EditOutlined, PlusOutlined } from '@ant-design/icons';
import { App as AntApp, Alert, Button, Descriptions, Drawer, Skeleton, Space, Tag } from 'antd';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';

import { branchNamesById } from '@/api/endpoints/branches.ts';
import { type PersonSummary } from '@/api/endpoints/persons.ts';
import { AssignMemberDialog, SelfProfileForm, useUnlinkUser } from '@/features/claims';
import { useActiveClan } from '@/hooks/useActiveClan.ts';
import { useAuth } from '@/hooks/useAuth.ts';
import { toMessage } from '@/lib/apiErrors.ts';

import { useBranches } from '../../branch';
import { useDeletePerson, usePerson } from '../hooks/useTree';
import { formatGeneration, formatLifespan, parentLabel, spouseLabel } from '../model/format';

import { PersonFormModal, type PersonFormMode } from './PersonFormModal';
import { RelationshipEditor } from './RelationshipEditor';
import {
  NoteBody,
  PersonMeta,
  PersonName,
  RelationGroup,
  RelationPerson,
  RelationRow,
  RelationTitle,
} from './styles';

interface PersonDrawerProps {
  clanId: string;
  personId: string | null;
  onClose: () => void;
  /** Opening a related person keeps the drawer open and swaps its subject. */
  onOpenPerson: (personId: string) => void;
}

/**
 * One person, and the family around them (F4.4, FR-T6).
 *
 * A drawer rather than a page, because the thing somebody is doing when they open a node is reading
 * the tree — and navigating away from it to read one card loses their place. Every related person is
 * a button that swaps the drawer's subject, so a family can be walked without leaving the list.
 *
 * **Read-only rendering is not "hidden buttons"** (F4.8). When `canEdit` is false the actions are
 * absent and an explanation takes their place naming what the caller can edit instead — a greyed-out
 * button that never works is worse than no button and a sentence.
 */
export function PersonDrawer({ clanId, personId, onClose, onOpenPerson }: PersonDrawerProps) {
  const { t } = useTranslation();
  const { message, modal } = AntApp.useApp();
  const { data: person, isPending, error } = usePerson(clanId, personId);
  const { data: branches } = useBranches(clanId);
  const deletePerson = useDeletePerson(clanId);
  const unlinkUser = useUnlinkUser(clanId);
  const { user } = useAuth();
  const { can } = useActiveClan();

  const [editing, setEditing] = useState<PersonFormMode | null>(null);
  const [assigning, setAssigning] = useState<{ id: string; fullName: string } | null>(null);

  /** Whether this node is the caller's own — what "this is you" and the self-service form need. */
  const isSelf = user !== null && person?.linkedUserId === user.id;
  const canApproveClaims = can('NODE_CLAIM_APPROVE');

  const onUnlink = (target: { id: string; fullName: string }, self: boolean) => {
    modal.confirm({
      title: t('claims.unlink.title', { person: target.fullName }),
      // Two different sentences, because they are two different acts: giving up your own place in
      // the book, and removing somebody else's. Neither deletes the node.
      content: self ? t('claims.unlink.bodySelf') : t('claims.unlink.bodyOther'),
      okText: t('claims.unlink.confirm'),
      cancelText: t('common.cancel'),
      okButtonProps: { danger: true },
      onOk: async () => {
        try {
          await unlinkUser.mutateAsync(target.id);
          void message.success(t('claims.unlink.done'));
        } catch (failure) {
          void message.error(toMessage(failure, t));
        }
      },
    });
  };

  // Through the whole hierarchy: the endpoint nests every chi under the root branch, and a
  // top-level `find` told everybody outside the root that they belonged to no branch at all.
  const branchName =
    person?.branchId === undefined
      ? undefined
      : branchNamesById(branches ?? []).get(person.branchId);

  const onDelete = () => {
    if (person === undefined) return;
    // Naming the children is the whole point of the confirmation (F4.9): a removal that strands
    // somebody is a removal the person should reconsider, and the server refuses it outright when
    // those children are outside their scope.
    const affected = person.children.map((child) => child.fullName);
    modal.confirm({
      title: t('tree.delete.title', { name: person.fullName }),
      content:
        affected.length === 0
          ? t('tree.delete.body')
          : t('tree.delete.bodyWithChildren', {
              count: affected.length,
              names: affected.join(', '),
            }),
      okText: t('tree.delete.confirm'),
      cancelText: t('common.cancel'),
      okButtonProps: { danger: true },
      onOk: async () => {
        try {
          await deletePerson.mutateAsync(person.id);
          void message.success(t('tree.delete.done'));
          onClose();
        } catch (failure) {
          void message.error(toMessage(failure, t));
        }
      },
    });
  };

  return (
    <Drawer
      open={personId !== null}
      onClose={onClose}
      width={520}
      title={person?.fullName ?? t('tree.drawer.title')}
      extra={
        person !== undefined &&
        person.canEdit && (
          <Space>
            <Button
              icon={<EditOutlined />}
              onClick={() => {
                setEditing({ kind: 'edit', person });
              }}
            >
              {t('tree.action.edit')}
            </Button>
            <Button danger icon={<DeleteOutlined />} onClick={onDelete}>
              {t('tree.action.delete')}
            </Button>
          </Space>
        )
      }
    >
      {error !== null && <Alert type="error" showIcon message={toMessage(error, t)} />}

      <Skeleton loading={isPending} active>
        {person !== undefined && (
          <>
            {!person.canEdit && (
              <Alert
                type="info"
                showIcon
                message={
                  branchName === undefined
                    ? t('tree.readOnly.noBranch')
                    : t('tree.readOnly.branch', { branch: branchName })
                }
              />
            )}

            <Descriptions column={1} size="small" bordered>
              <Descriptions.Item label={t('tree.field.generation')}>
                {formatGeneration(person.generation, t)}
              </Descriptions.Item>
              <Descriptions.Item label={t('tree.field.gender')}>
                {t(`tree.gender.${person.gender}`)}
              </Descriptions.Item>
              <Descriptions.Item label={t('tree.field.lifeStatus')}>
                {t(`tree.lifeStatus.${person.lifeStatus}`)}
              </Descriptions.Item>
              <Descriptions.Item label={t('tree.field.lifespan')}>
                {formatLifespan(person, t) ?? t('tree.unknown')}
              </Descriptions.Item>
              {person.alias !== undefined && (
                <Descriptions.Item label={t('tree.field.alias')}>{person.alias}</Descriptions.Item>
              )}
              {person.posthumousName !== undefined && (
                <Descriptions.Item label={t('tree.field.posthumousName')}>
                  {person.posthumousName}
                </Descriptions.Item>
              )}
              {person.burialPlace !== undefined && (
                <Descriptions.Item label={t('tree.field.burialPlace')}>
                  {person.burialPlace}
                </Descriptions.Item>
              )}
              <Descriptions.Item label={t('tree.field.branch')}>
                {branchName ?? t('tree.unplaced')}
              </Descriptions.Item>
              <Descriptions.Item label={t('tree.field.linkedUser')}>
                <Space wrap>
                  {person.linkedUserId === undefined ? (
                    <Tag>{t('tree.notLinked')}</Tag>
                  ) : isSelf ? (
                    // F6.7. Said as plainly as the tree canvas says it, and in the same words.
                    <Tag color="green">{t('claims.thisIsYou')}</Tag>
                  ) : (
                    <Tag color="green">{t('tree.linked')}</Tag>
                  )}

                  {person.linkedUserId === undefined && canApproveClaims && (
                    <Button
                      size="small"
                      type="link"
                      onClick={() => {
                        setAssigning({ id: person.id, fullName: person.fullName });
                      }}
                    >
                      {t('claims.assign.action')}
                    </Button>
                  )}

                  {person.linkedUserId !== undefined && (isSelf || canApproveClaims) && (
                    <Button
                      size="small"
                      type="link"
                      danger
                      onClick={() => {
                        onUnlink({ id: person.id, fullName: person.fullName }, isSelf);
                      }}
                    >
                      {t('claims.unlink.action')}
                    </Button>
                  )}
                </Space>
              </Descriptions.Item>
            </Descriptions>

            {person.notesHtml !== undefined && person.notesHtml !== '' && (
              <RelationGroup>
                <RelationTitle>{t('tree.field.notes')}</RelationTitle>
                {/* Sanitized on write against an allow-list (B4.13), so what is stored is already
                    safe to render — the client never re-sanitizes, because a second sanitizer is a
                    second set of parsing bugs. */}
                <NoteBody dangerouslySetInnerHTML={{ __html: person.notesHtml }} />
              </RelationGroup>
            )}

            {isSelf && (
              <RelationGroup>
                <RelationTitle>{t('claims.self.title')}</RelationTitle>
                <SelfProfileForm
                  clanId={clanId}
                  person={{
                    id: person.id,
                    ...(person.notesHtml === undefined ? {} : { notesHtml: person.notesHtml }),
                  }}
                />
              </RelationGroup>
            )}

            <RelationGroup>
              <RelationTitle>{t('tree.relation.parents')}</RelationTitle>
              {person.parents.length === 0 && <PersonMeta>{t('tree.relation.none')}</PersonMeta>}
              {person.parents.map((parent) => (
                <RelationRow key={parent.parentageId}>
                  <RelationPerson
                    type="button"
                    onClick={() => {
                      onOpenPerson(parent.person.id);
                    }}
                  >
                    <PersonName>{parent.person.fullName}</PersonName>
                    <PersonMeta>
                      {parentLabel(parent.parentRole, t)}
                      {parent.nature !== 'BIOLOGICAL'
                        ? ` · ${t(`tree.nature.${parent.nature}`)}`
                        : ''}
                    </PersonMeta>
                  </RelationPerson>
                </RelationRow>
              ))}
            </RelationGroup>

            <RelationGroup>
              <RelationTitle>{t('tree.relation.unions')}</RelationTitle>
              {person.unions.length === 0 && <PersonMeta>{t('tree.relation.none')}</PersonMeta>}
              {person.unions.map((link) => (
                <RelationRow key={link.union.id}>
                  <RelationPerson
                    type="button"
                    onClick={() => {
                      onOpenPerson(link.partner.id);
                    }}
                  >
                    <PersonName>{link.partner.fullName}</PersonName>
                    <PersonMeta>
                      {spouseLabel(link.partner, t)} · {t(`tree.unionType.${link.union.unionType}`)}
                    </PersonMeta>
                  </RelationPerson>
                </RelationRow>
              ))}
            </RelationGroup>

            <RelationGroup>
              <RelationTitle>{t('tree.relation.children')}</RelationTitle>
              {person.children.length === 0 && <PersonMeta>{t('tree.relation.none')}</PersonMeta>}
              {person.children.map((child) => (
                <PersonRow key={child.id} person={child} onOpen={onOpenPerson} />
              ))}
              {person.canEdit && (
                <Button
                  type="link"
                  icon={<PlusOutlined />}
                  onClick={() => {
                    setEditing({
                      kind: 'create',
                      placementType: 'CHILD_OF_PARENT',
                      anchorPersonId: person.id,
                    });
                  }}
                >
                  {t('tree.action.addChild')}
                </Button>
              )}
            </RelationGroup>

            <RelationGroup>
              <RelationTitle>{t('tree.relation.siblings')}</RelationTitle>
              {person.siblings.length === 0 && <PersonMeta>{t('tree.relation.none')}</PersonMeta>}
              {person.siblings.map((sibling) => (
                <PersonRow key={sibling.id} person={sibling} onOpen={onOpenPerson} />
              ))}
            </RelationGroup>

            {person.canEdit && <RelationshipEditor clanId={clanId} person={person} />}
          </>
        )}
      </Skeleton>

      <AssignMemberDialog
        clanId={clanId}
        person={assigning}
        onClose={() => {
          setAssigning(null);
        }}
      />

      {editing !== null && (
        <PersonFormModal
          clanId={clanId}
          open
          mode={editing}
          onClose={() => {
            setEditing(null);
          }}
        />
      )}
    </Drawer>
  );
}

interface PersonRowProps {
  person: PersonSummary;
  onOpen: (personId: string) => void;
}

function PersonRow({ person, onOpen }: PersonRowProps) {
  const { t } = useTranslation();
  return (
    <RelationRow>
      <RelationPerson
        type="button"
        onClick={() => {
          onOpen(person.id);
        }}
      >
        <PersonName $readOnly={!person.canEdit}>{person.fullName}</PersonName>
        <PersonMeta>
          {formatGeneration(person.generation, t)}
          {formatLifespan(person, t) === undefined ? '' : ` · ${formatLifespan(person, t) ?? ''}`}
        </PersonMeta>
      </RelationPerson>
    </RelationRow>
  );
}
