import { App as AntApp, Alert, Input, Modal } from 'antd';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';

import { type UnlinkedCandidate } from '@/api/endpoints/links.ts';
import { toMessage } from '@/lib/apiErrors.ts';

import { useMyLinkedNode, useSubmitClaim } from '../hooks/useClaims';
import { DialogBody, FieldLabel, TreeContext } from '../pages/styles';

interface ClaimDialogProps {
  clanId: string;
  /** The node being claimed. Null closes the dialog. */
  candidate: UnlinkedCandidate | null;
  onClose: () => void;
}

/**
 * "This is me" — the claim submission dialog (F6.3).
 *
 * Three things it has to say, in this order:
 *
 * 1. **Which node**, with the family around it. Somebody who picks the wrong Nguyễn Văn An should
 *    notice here, not after an editor refuses them.
 * 2. **That a person will read it.** This is a request, not a switch; saying so is what stops the
 *    dialog feeling broken when nothing happens on submit.
 * 3. **Why they cannot**, when they already hold a node in this clan (I-P4). Blocking the button
 *    without a sentence is the version of this screen people file bugs about.
 */
export function ClaimDialog({ clanId, candidate, onClose }: ClaimDialogProps) {
  const { t } = useTranslation();
  const { message } = AntApp.useApp();
  const submit = useSubmitClaim(clanId);
  const { data: myNode } = useMyLinkedNode(clanId);

  const [evidence, setEvidence] = useState('');

  const alreadyLinked = myNode !== undefined && myNode !== null;

  const onSubmit = async () => {
    if (candidate === null) return;
    try {
      await submit.mutateAsync({
        personId: candidate.person.id,
        ...(evidence.trim() === '' ? {} : { evidence: evidence.trim() }),
      });
      void message.success(t('claims.submit.done'));
      setEvidence('');
      onClose();
    } catch (failure) {
      void message.error(toMessage(failure, t));
    }
  };

  return (
    <Modal
      open={candidate !== null}
      title={t('claims.submit.title', { name: candidate?.person.fullName ?? '' })}
      okText={t('claims.submit.confirm')}
      cancelText={t('common.cancel')}
      okButtonProps={{ disabled: alreadyLinked }}
      confirmLoading={submit.isPending}
      onOk={() => void onSubmit()}
      onCancel={onClose}
    >
      <DialogBody>
        {alreadyLinked && (
          <Alert
            type="warning"
            showIcon
            message={t('claims.submit.alreadyLinked', { name: myNode.personName ?? '' })}
          />
        )}

        {candidate !== null && (
          <TreeContext>
            {describeFamily(
              candidate,
              t('claims.candidate.parents'),
              t('claims.candidate.children'),
            )}
          </TreeContext>
        )}

        <Alert type="info" showIcon message={t('claims.submit.reviewed')} />

        <div>
          <FieldLabel htmlFor="claim-evidence">{t('claims.submit.evidenceLabel')}</FieldLabel>
          <Input.TextArea
            id="claim-evidence"
            rows={3}
            maxLength={2000}
            showCount
            placeholder={t('claims.submit.evidencePlaceholder')}
            value={evidence}
            disabled={alreadyLinked}
            onChange={(event) => {
              setEvidence(event.target.value);
            }}
          />
        </div>
      </DialogBody>
    </Modal>
  );
}

/**
 * The family around a candidate, as one line.
 *
 * Exported-shaped rather than inlined because both the dialog and the browser row render it, and
 * two versions of "father: …, children: …" would drift the first time somebody reworded one.
 */
export function describeFamily(
  candidate: { parentNames: string[]; childNames: string[] },
  parentsLabel: string,
  childrenLabel: string,
): string {
  const parts: string[] = [];
  if (candidate.parentNames.length > 0) {
    parts.push(`${parentsLabel}: ${candidate.parentNames.join(', ')}`);
  }
  if (candidate.childNames.length > 0) {
    parts.push(`${childrenLabel}: ${candidate.childNames.join(', ')}`);
  }
  return parts.join(' · ');
}
