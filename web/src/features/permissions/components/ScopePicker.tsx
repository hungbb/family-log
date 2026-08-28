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
 * The subtree option is <b>present and disabled</b> rather than absent. Doc 04 describes three
 * scopes and Phase 4 turns the third on; a picker that simply omitted it would read as a product
 * that does not have the feature, and the person who was told it does would go looking for it
 * somewhere else. Disabled with an explanation is the honest state.
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
            value: 'SUBTREE',
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
