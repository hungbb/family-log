import { App as AntApp, Alert, Button, Input, Skeleton, Switch } from 'antd';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';

import { toMessage } from '@/lib/apiErrors.ts';

import { usePropagationFlags, useUpdateSelfProfile } from '../hooks/useClaims';
import {
  DialogBody,
  FieldLabel,
  InlineActions,
  Meta,
  ToggleRow,
  ToggleText,
} from '../pages/styles';

/** The three editable values, as one piece of state. */
interface Draft {
  notes: string;
  avatar: boolean;
  displayName: boolean;
}

interface SelfProfileFormProps {
  clanId: string;
  person: { id: string; notesHtml?: string };
}

/**
 * What somebody may change about their own node (FR-N6, F6.9, doc 04 §6).
 *
 * **The whitelist is two fields**: their photograph and their own notes. Everything else in the
 * record — name, đời, dates, parents, spouses — is the family's account of them, and changing it
 * needs `TREE_EDIT` like any other edit. That is not an oversight to be widened when somebody asks;
 * extending it takes an ADR (doc 06 §2).
 *
 * So the form says which fields it does not own, in a sentence, rather than showing them greyed
 * out. A disabled input reads as "not yet"; a sentence naming who to ask reads as the rule it is.
 *
 * The propagation switches are opt-in and off by default. Copying an account's avatar and display
 * name onto a genealogical record is a decision about a book that outlives the account, so nobody
 * makes it silently on the person's behalf — and the display name lands on `alias`, never on the
 * recorded `full_name`.
 */
export function SelfProfileForm({ clanId, person }: SelfProfileFormProps) {
  const { t } = useTranslation();
  const { message } = AntApp.useApp();
  const { data: flags, isPending } = usePropagationFlags(clanId, person.id, true);
  const update = useUpdateSelfProfile(clanId);

  /**
   * What the person has typed, or nothing yet.
   *
   * One nullable draft rather than three pieces of state synchronised to the server by an effect:
   * until somebody touches the form, what it shows *is* the server's answer, so it follows a
   * refetch — a flag turned on in another tab, or the drawer being pointed at a different node —
   * without any resetting logic to get wrong. Saving clears the draft, so the form goes back to
   * mirroring what was actually stored.
   */
  const [draft, setDraft] = useState<Draft | null>(null);

  const current: Draft = draft ?? {
    notes: person.notesHtml ?? '',
    avatar: flags?.propagateAvatar ?? false,
    displayName: flags?.propagateDisplayName ?? false,
  };
  const edit = (change: Partial<Draft>) => {
    setDraft({ ...current, ...change });
  };

  const onSave = async () => {
    try {
      await update.mutateAsync({
        personId: person.id,
        input: {
          notesHtml: current.notes,
          propagateAvatar: current.avatar,
          propagateDisplayName: current.displayName,
        },
      });
      void message.success(t('claims.self.saved'));
      // Back to mirroring the server, which the invalidation is about to refetch.
      setDraft(null);
    } catch (failure) {
      void message.error(toMessage(failure, t));
    }
  };

  if (isPending) {
    return <Skeleton active />;
  }

  return (
    // The same vertical stack the dialogs use — this form renders inside the person drawer.
    <DialogBody>
      <Alert type="info" showIcon message={t('claims.self.scope')} />

      <div>
        <FieldLabel htmlFor="self-notes">{t('claims.self.notesLabel')}</FieldLabel>
        <Input.TextArea
          id="self-notes"
          rows={4}
          maxLength={4000}
          showCount
          value={current.notes}
          onChange={(event) => {
            edit({ notes: event.target.value });
          }}
        />
        <Meta>{t('claims.self.notesHelp')}</Meta>
      </div>

      <ToggleRow>
        <Switch
          id="propagate-avatar"
          checked={current.avatar}
          onChange={(checked) => {
            edit({ avatar: checked });
          }}
        />
        <ToggleText>
          <FieldLabel htmlFor="propagate-avatar">{t('claims.self.propagateAvatar')}</FieldLabel>
          <Meta>{t('claims.self.propagateAvatarHelp')}</Meta>
        </ToggleText>
      </ToggleRow>

      <ToggleRow>
        <Switch
          id="propagate-name"
          checked={current.displayName}
          onChange={(checked) => {
            edit({ displayName: checked });
          }}
        />
        <ToggleText>
          <FieldLabel htmlFor="propagate-name">{t('claims.self.propagateDisplayName')}</FieldLabel>
          <Meta>{t('claims.self.propagateDisplayNameHelp')}</Meta>
        </ToggleText>
      </ToggleRow>

      <InlineActions>
        <Button type="primary" loading={update.isPending} onClick={() => void onSave()}>
          {t('common.save')}
        </Button>
      </InlineActions>
    </DialogBody>
  );
}
