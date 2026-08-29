import { App as AntApp, Alert, Modal, Select } from 'antd';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';

import { useMembers } from '@/features/members';
import { toMessage } from '@/lib/apiErrors.ts';

import { useLinkUser } from '../hooks/useClaims';
import { DialogBody, FieldLabel, FieldRow } from '../pages/styles';

interface AssignMemberDialogProps {
  clanId: string;
  /** The node being assigned. Null closes the dialog. */
  person: { id: string; fullName: string } | null;
  onClose: () => void;
}

/**
 * Direct assignment by an editor (FR-N4, F6.6).
 *
 * The shortcut for the ordinary case: a *trưởng họ* sitting with a relative, or filling in the
 * record for an aunt who will never open the site herself. It is the same write the claim workflow
 * ends in, minus the request.
 *
 * Only active members can be picked, because a link to a non-member breaks I-P5 and the server
 * would refuse it anyway. A member who already holds a node is shown but not selectable, with the
 * node named — "why is my sister missing from this list" is otherwise the next question.
 *
 * Two steps: pick, then confirm. Assigning somebody else's identity to a node is not something to
 * do with one stray click on a long list.
 */
export function AssignMemberDialog({ clanId, person, onClose }: AssignMemberDialogProps) {
  const { t } = useTranslation();
  const { message } = AntApp.useApp();
  const link = useLinkUser(clanId);

  const [search, setSearch] = useState('');
  const [userId, setUserId] = useState<string | undefined>(undefined);

  const { data: members, isPending } = useMembers(clanId, {
    ...(search !== '' ? { search } : {}),
    page: 0,
    size: 50,
  });

  const options = (members?.content ?? []).map((member) => ({
    value: member.userId,
    label:
      member.linkedPersonId === undefined
        ? (member.displayName ?? member.email ?? member.userId)
        : t('claims.assign.alreadyHolds', {
            name: member.displayName ?? member.email ?? member.userId,
          }),
    disabled: member.linkedPersonId !== undefined,
  }));

  const close = () => {
    setUserId(undefined);
    setSearch('');
    onClose();
  };

  const onAssign = async () => {
    if (person === null || userId === undefined) return;
    try {
      await link.mutateAsync({ personId: person.id, userId });
      void message.success(t('claims.assign.done', { person: person.fullName }));
      close();
    } catch (failure) {
      void message.error(toMessage(failure, t));
    }
  };

  const chosen = (members?.content ?? []).find((member) => member.userId === userId);

  return (
    <Modal
      open={person !== null}
      title={t('claims.assign.title', { person: person?.fullName ?? '' })}
      okText={t('claims.assign.confirm')}
      cancelText={t('common.cancel')}
      okButtonProps={{ disabled: userId === undefined }}
      confirmLoading={link.isPending}
      onOk={() => void onAssign()}
      onCancel={close}
    >
      <DialogBody>
        <div>
          <FieldLabel htmlFor="assign-member">{t('claims.assign.memberLabel')}</FieldLabel>
          <FieldRow>
            <Select
              id="assign-member"
              showSearch
              allowClear
              loading={isPending}
              filterOption={false}
              placeholder={t('claims.assign.memberPlaceholder')}
              value={userId}
              options={options}
              onSearch={(value) => {
                setSearch(value.trim());
              }}
              onChange={(value: string | undefined) => {
                setUserId(value);
              }}
            />
          </FieldRow>
        </div>

        {chosen !== undefined && (
          <Alert
            type="warning"
            showIcon
            message={t('claims.assign.confirmBody', {
              name: chosen.displayName ?? chosen.email ?? chosen.userId,
              person: person?.fullName ?? '',
            })}
            description={t('claims.assign.noEditRights')}
          />
        )}
      </DialogBody>
    </Modal>
  );
}
