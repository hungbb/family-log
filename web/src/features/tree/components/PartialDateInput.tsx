import { Checkbox, Input, InputNumber, Segmented, Space } from 'antd';
import { useTranslation } from 'react-i18next';

import { type DatePrecision, type PartialDate } from '@/api/endpoints/persons.ts';

import { DateFieldRow, DateHint } from './styles';

interface PartialDateInputProps {
  // `exactOptionalPropertyTypes` is on (doc 09 §4.3), so an optional prop and a prop that may be
  // `undefined` are different types — and AntD's `Form.Item` passes the latter.
  value?: PartialDate | undefined;
  onChange?: ((value: PartialDate | undefined) => void) | undefined;
  disabled?: boolean | undefined;
  /** Names the field for screen readers — "Born", "Died", "Married". */
  label: string;
}

const PRECISIONS: DatePrecision[] = ['UNKNOWN', 'YEAR', 'MONTH', 'EXACT', 'RANGE'];

/**
 * A date as a family book actually records it (F4.1).
 *
 * **The precision is the first control, not the last.** Most of what goes into a *gia phả* is a year
 * and nothing more, and a date picker that demanded a day would be a form that invented one — which
 * is indistinguishable from a recorded fact the moment it is stored. So the person entering the
 * record says how much they know first, and only the fields that follow from that appear.
 *
 * `circa` is a checkbox rather than a precision of its own because it is orthogonal: "khoảng tháng 3
 * năm 1901" is a real thing to write. The lunar field is free text, always available, and never
 * converted — the âm lịch reading is the family's own words and the product keeps them.
 *
 * Written as a controlled component with `value`/`onChange` so AntD's `Form.Item` drives it like any
 * other input.
 */
export function PartialDateInput({ value, onChange, disabled, label }: PartialDateInputProps) {
  const { t } = useTranslation();
  const precision = value?.precision ?? 'UNKNOWN';

  /**
   * Applies one changed field and hands the whole date back.
   *
   * Cleared fields are dropped rather than sent as `undefined`. Under
   * `exactOptionalPropertyTypes` (doc 09 §4.3) an absent key and a key holding `undefined` are
   * different things, and the difference reaches the wire: the server distinguishes "no month" from
   * "month: null" and refuses the second at YEAR precision.
   */
  const emit = (next: { [K in keyof PartialDate]?: PartialDate[K] | undefined }) => {
    if (onChange === undefined) return;
    const source = { ...(value ?? {}), ...next };
    const merged: PartialDate = {
      precision: source.precision ?? precision,
      circa: source.circa ?? false,
      ...defined('year', source.year),
      ...defined('month', source.month),
      ...defined('day', source.day),
      ...defined('rangeEndYear', source.rangeEndYear),
      ...defined('lunarText', source.lunarText),
    };
    // An unknown date carries nothing else — the server refuses a year at UNKNOWN precision, and
    // sending one would turn a cleared field into a 422.
    onChange(merged.precision === 'UNKNOWN' ? undefined : merged);
  };

  const onPrecisionChange = (next: DatePrecision) => {
    if (onChange === undefined) return;
    if (next === 'UNKNOWN') {
      onChange(undefined);
      return;
    }
    // Dropping the parts the new precision does not carry, because the server's shape rules refuse
    // a month at YEAR precision — and because a hidden field that still travels is a field that
    // silently reappears when somebody widens the precision again.
    const year = value?.year ?? new Date().getFullYear();
    const base: PartialDate = {
      precision: next,
      circa: value?.circa ?? false,
      year,
      ...(value?.lunarText !== undefined ? { lunarText: value.lunarText } : {}),
    };
    if (next === 'MONTH' || next === 'EXACT') base.month = value?.month ?? 1;
    if (next === 'EXACT') base.day = value?.day ?? 1;
    if (next === 'RANGE') base.rangeEndYear = value?.rangeEndYear ?? year;
    onChange(base);
  };

  return (
    <Space direction="vertical" size="small">
      <Segmented
        disabled={disabled ?? false}
        value={precision}
        aria-label={t('tree.date.precisionFor', { field: label })}
        onChange={(next) => {
          onPrecisionChange(next);
        }}
        options={PRECISIONS.map((option) => ({
          value: option,
          label: t(`tree.date.precision.${option}`),
        }))}
      />

      {precision !== 'UNKNOWN' && (
        <DateFieldRow>
          <InputNumber
            disabled={disabled ?? false}
            min={1}
            max={2999}
            value={value?.year ?? null}
            aria-label={t('tree.date.year')}
            placeholder={t('tree.date.year')}
            onChange={(year) => {
              emit({ year: year ?? undefined });
            }}
          />

          {(precision === 'MONTH' || precision === 'EXACT') && (
            <InputNumber
              disabled={disabled ?? false}
              min={1}
              max={12}
              value={value?.month ?? null}
              aria-label={t('tree.date.month')}
              placeholder={t('tree.date.month')}
              onChange={(month) => {
                emit({ month: month ?? undefined });
              }}
            />
          )}

          {precision === 'EXACT' && (
            <InputNumber
              disabled={disabled ?? false}
              min={1}
              max={31}
              value={value?.day ?? null}
              aria-label={t('tree.date.day')}
              placeholder={t('tree.date.day')}
              onChange={(day) => {
                emit({ day: day ?? undefined });
              }}
            />
          )}

          {precision === 'RANGE' && (
            <InputNumber
              disabled={disabled ?? false}
              min={1}
              max={2999}
              value={value?.rangeEndYear ?? null}
              aria-label={t('tree.date.rangeEnd')}
              placeholder={t('tree.date.rangeEnd')}
              onChange={(rangeEndYear) => {
                emit({ rangeEndYear: rangeEndYear ?? undefined });
              }}
            />
          )}

          <Checkbox
            disabled={disabled ?? false}
            checked={value?.circa ?? false}
            onChange={(event) => {
              emit({ circa: event.target.checked });
            }}
          >
            {t('tree.date.circaLabel')}
          </Checkbox>
        </DateFieldRow>
      )}

      <Input
        disabled={disabled ?? false}
        maxLength={100}
        value={value?.lunarText ?? ''}
        aria-label={t('tree.date.lunar')}
        placeholder={t('tree.date.lunarPlaceholder')}
        onChange={(event) => {
          const lunarText = event.target.value;
          if (onChange === undefined) return;
          if (value === undefined) {
            // A lunar reading with no Gregorian year is not something the server can store, so the
            // field waits until a precision has been chosen rather than silently discarding it.
            return;
          }
          onChange({ ...value, lunarText });
        }}
      />
      {value === undefined && <DateHint>{t('tree.date.lunarNeedsPrecision')}</DateHint>}
    </Space>
  );
}

/** `{ key: value }`, or nothing at all when the value was cleared. */
function defined<K extends string, V>(key: K, value: V | undefined): Partial<Record<K, V>> {
  return value === undefined ? {} : ({ [key]: value } as Record<K, V>);
}
