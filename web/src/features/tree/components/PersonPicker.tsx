import { Select, Spin } from 'antd';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';

import { type PersonSummary } from '@/api/endpoints/persons.ts';

import { usePersonSearch } from '../hooks/useTree';
import { formatGeneration, formatLifespan } from '../model/format';

interface PersonPickerProps {
  clanId: string;
  // `exactOptionalPropertyTypes` is on: an optional prop and one that may be `undefined` are
  // different types, and every caller here passes state that starts undefined.
  value?: string | undefined;
  onChange?: ((personId: string | undefined) => void) | undefined;
  disabled?: boolean | undefined;
  placeholder?: string | undefined;
  /** Ids to leave out — a person is never their own parent, and rarely their own spouse. */
  exclude?: string[];
  /** Only offer people the caller may edit. Used where the server will refuse the others anyway. */
  editableOnly?: boolean;
}

/**
 * Choosing a person by searching for them (F4.2).
 *
 * A search box rather than a dropdown of everybody: a clan can hold thousands of people, and the
 * list this is picking from is the whole tree. The query runs server-side and is
 * diacritic-insensitive in both directions, so "nguyen van" finds "Nguyễn Văn An" and the person
 * typing never has to reach for a Vietnamese keyboard.
 *
 * Each option carries the đời and the lifespan, because a family book contains several people with
 * the same name and those two lines are how anybody tells them apart.
 */
export function PersonPicker({
  clanId,
  value,
  onChange,
  disabled,
  placeholder,
  exclude = [],
  editableOnly = false,
}: PersonPickerProps) {
  const { t } = useTranslation();
  const [term, setTerm] = useState('');

  const { data, isFetching } = usePersonSearch(clanId, {
    ...(term === '' ? {} : { q: term }),
    size: 20,
  });

  const options = (data?.content ?? [])
    .filter((person) => !exclude.includes(person.id))
    .filter((person) => !editableOnly || person.canEdit)
    .map((person) => ({
      value: person.id,
      label: describe(person, t),
    }));

  return (
    <Select
      showSearch
      allowClear
      disabled={disabled ?? false}
      value={value ?? undefined}
      placeholder={placeholder ?? t('tree.picker.placeholder')}
      // The server does the matching, so the client must not filter the results again — its own
      // matching does not fold diacritics and would hide exactly the rows the query found.
      filterOption={false}
      onSearch={setTerm}
      onChange={(next: string | undefined) => onChange?.(next)}
      notFoundContent={isFetching ? <Spin size="small" /> : t('tree.picker.empty')}
      options={options}
    />
  );
}

function describe(person: PersonSummary, t: ReturnType<typeof useTranslation>['t']): string {
  const lifespan = formatLifespan(person, t);
  const generation = formatGeneration(person.generation, t);
  return lifespan === undefined
    ? `${person.fullName} · ${generation}`
    : `${person.fullName} · ${generation} · ${lifespan}`;
}
