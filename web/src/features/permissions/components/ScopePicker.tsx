import { Select } from 'antd';
import { useTranslation } from 'react-i18next';

import { type GrantableScope } from '@/api/endpoints/permissions.ts';

import { FieldRow } from '../pages/styles';

/**
 * "Where should this apply?" — clan-wide, or one branch of the tree (F3.6).
 *
 * The options come from `/permissions/grantable` and nowhere else, so the picker can only ever
 * offer a scope the caller may actually grant at. Building it from the branch tree instead would
 * mean showing every chi in the clan and refusing most of them on submit.
 *
 * The subtree option is not one of those: a subtree scope names a person, and a clan has thousands
 * of them, so it cannot be enumerated alongside the branches. Choosing it reveals a person search
 * instead (see {@link GrantDialog}), and the server resolves whoever comes back. It is present and
 * disabled rather than absent for somebody who cannot grant it — a branch role manager, whom the
 * server refuses a person scope even inside their own chi — so the picker says who can, instead of
 * offering a choice that ends in "escalation blocked".
 */
interface ScopePickerProps {
  scopes: GrantableScope[];
  subtreeAvailable: boolean;
  /** The `scopeRefId`, or `CLAN` for the clan-wide option. */
  value: string | undefined;
  onChange: (value: string) => void;
  disabled?: boolean;
}

/** The sentinel the clan-wide option carries, since it has no `scopeRefId` of its own. */
export const CLAN_SCOPE_VALUE = 'CLAN';

/**
 * The sentinel for the subtree option.
 *
 * It carries no id either, for the opposite reason: the id is a person, and *which* person is a
 * second question the dialog asks with a search box.
 */
export const SUBTREE_SCOPE_VALUE = 'SUBTREE';

export function ScopePicker({
  scopes,
  subtreeAvailable,
  value,
  onChange,
  disabled = false,
}: ScopePickerProps) {
  const { t } = useTranslation();

  const options = scopes.map((scope) => ({
    value: scope.scopeType === 'CLAN' ? CLAN_SCOPE_VALUE : (scope.scopeRefId ?? CLAN_SCOPE_VALUE),
    // Indented by depth so the branch tree's shape survives being flattened into a list.
    label:
      scope.scopeType === 'CLAN'
        ? t('permissions.scope.wholeClan')
        : `${'  '.repeat(scope.depth)}${scope.label ?? ''}`,
    disabled: false,
  }));

  return (
    <FieldRow>
      <Select
        showSearch
        optionFilterProp="label"
        placeholder={t('permissions.scope.placeholder')}
        // See the note in GrantDialog: null is how AntD spells "nothing chosen".
        value={value ?? null}
        disabled={disabled}
        onChange={onChange}
        options={[
          ...options,
          {
            value: SUBTREE_SCOPE_VALUE,
            label: subtreeAvailable
              ? t('permissions.scope.subtree')
              : t('permissions.scope.subtreeUnavailable'),
            disabled: !subtreeAvailable,
          },
        ]}
      />
    </FieldRow>
  );
}
