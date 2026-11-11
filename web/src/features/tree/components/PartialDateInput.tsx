import { Checkbox, Input, InputNumber, Segmented, Space } from 'antd';
import { useTranslation } from 'react-i18next';

import { type DatePrecision, type PartialDate } from '@/api/endpoints/persons.ts';

import { withField, withLunarText, withPrecision } from '../model/partialDate';

import { DateFieldRow } from './styles';

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
 * năm 1901" is a real thing to write. The lunar field is free text, always available — with or
 * without a Gregorian date, since an anniversary is often all a family knows — and never converted:
 * the âm lịch reading is the family's own words and the product keeps them.
 *
 * Written as a controlled component with `value`/`onChange` so AntD's `Form.Item` drives it like any
 * other input.
 */
export function PartialDateInput({ value, onChange, disabled, label }: PartialDateInputProps) {
  const { t } = useTranslation();
  const precision = value?.precision ?? 'UNKNOWN';

  // Every edit produces the next whole date — or `undefined` when nothing is left — through the
  // pure transitions in `model/partialDate.ts`, which is where the rules about what each precision
  // may carry are written down and tested.
  const emit = (next: PartialDate | undefined) => {
    onChange?.(next);
  };

  return (
    <Space direction="vertical" size="small">
      <Segmented
        disabled={disabled ?? false}
        value={precision}
        aria-label={t('tree.date.precisionFor', { field: label })}
        onChange={(next) => {
          emit(withPrecision(value, next, new Date().getFullYear()));
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
              emit(withField(value, { year: year ?? undefined }));
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
                emit(withField(value, { month: month ?? undefined }));
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
                emit(withField(value, { day: day ?? undefined }));
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
                emit(withField(value, { rangeEndYear: rangeEndYear ?? undefined }));
              }}
            />
          )}

          <Checkbox
            disabled={disabled ?? false}
            checked={value?.circa ?? false}
            onChange={(event) => {
              emit(withField(value, { circa: event.target.checked }));
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
          // Works with no precision chosen: an anniversary with no year is a date the book has.
          emit(withLunarText(value, event.target.value));
        }}
      />
    </Space>
  );
}
