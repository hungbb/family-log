import { type DatePrecision, type PartialDate } from '@/api/endpoints/persons.ts';

/**
 * The edits a person can make to a partial date, as pure transitions (F4.1).
 *
 * `PartialDateInput` is a controlled component: every keystroke produces the *next whole date*, or
 * `undefined` when nothing is left. Those rules live here rather than in the component so that
 * what the server will and will not accept — no month at `YEAR` precision, no year at `UNKNOWN` —
 * is stated once and can be tested without rendering anything.
 *
 * **A lunar reading needs no Gregorian date.** "Giỗ ngày mồng 3 tháng Chạp" with no year is the
 * commonest date in a family book, and the server stores it as `UNKNOWN` precision plus the text.
 * So an "unknown" date is only *absent* when it also has no lunar reading — the one place this
 * module used to get wrong, by refusing the text until a precision had been chosen and by dropping
 * it whenever the precision went back to unknown.
 */

type DatePatch = { [K in keyof PartialDate]?: PartialDate[K] | undefined };

/** A date with nothing in it is no date: the form field is cleared rather than sent empty. */
function orAbsent(date: PartialDate): PartialDate | undefined {
  return date.precision === 'UNKNOWN' && date.lunarText === undefined ? undefined : date;
}

/** `{ key: value }`, or nothing at all when the value was cleared. */
function defined<K extends string, V>(key: K, value: V | undefined): Partial<Record<K, V>> {
  return value === undefined ? {} : ({ [key]: value } as Record<K, V>);
}

/** The lunar reading as it should travel: trimmed of nothing, but absent when empty. */
function lunarOf(text: string | undefined): string | undefined {
  return text === undefined || text.trim() === '' ? undefined : text;
}

/**
 * Applies one changed field.
 *
 * Cleared fields are dropped rather than sent as `undefined`. Under `exactOptionalPropertyTypes`
 * (doc 09 §4.3) an absent key and a key holding `undefined` are different things, and the
 * difference reaches the wire: the server distinguishes "no month" from "month: null" and refuses
 * the second at YEAR precision.
 */
export function withField(
  value: PartialDate | undefined,
  patch: DatePatch,
): PartialDate | undefined {
  const source = { ...(value ?? {}), ...patch };
  return orAbsent({
    precision: source.precision ?? 'UNKNOWN',
    circa: source.circa ?? false,
    ...defined('year', source.year),
    ...defined('month', source.month),
    ...defined('day', source.day),
    ...defined('rangeEndYear', source.rangeEndYear),
    ...defined('lunarText', lunarOf(source.lunarText)),
  });
}

/**
 * Changes how much of the date is known.
 *
 * Drops the parts the new precision does not carry, because the server's shape rules refuse a month
 * at YEAR precision — and because a hidden field that still travels is a field that silently
 * reappears when somebody widens the precision again. The lunar reading is not such a part: it
 * belongs to the date at every precision, including none.
 *
 * @param thisYear the year a newly dated field starts from; a parameter so this stays pure
 */
export function withPrecision(
  value: PartialDate | undefined,
  next: DatePrecision,
  thisYear: number,
): PartialDate | undefined {
  const lunar = defined('lunarText', lunarOf(value?.lunarText));
  if (next === 'UNKNOWN') {
    return orAbsent({ precision: 'UNKNOWN', circa: false, ...lunar });
  }
  const year = value?.year ?? thisYear;
  const base: PartialDate = { precision: next, circa: value?.circa ?? false, year, ...lunar };
  if (next === 'MONTH' || next === 'EXACT') base.month = value?.month ?? 1;
  if (next === 'EXACT') base.day = value?.day ?? 1;
  if (next === 'RANGE') base.rangeEndYear = value?.rangeEndYear ?? year;
  return base;
}

/**
 * Sets or clears the lunar reading.
 *
 * On a field with no date yet this *creates* one, at `UNKNOWN` precision — the family knows the
 * anniversary and not the year, and that is a complete answer.
 */
export function withLunarText(
  value: PartialDate | undefined,
  text: string,
): PartialDate | undefined {
  const { lunarText: _previous, ...rest } = value ?? {
    precision: 'UNKNOWN' as const,
    circa: false,
  };
  return orAbsent({ ...rest, ...defined('lunarText', lunarOf(text)) });
}
