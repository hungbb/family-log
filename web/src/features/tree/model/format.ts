import { type TFunction } from 'i18next';

import { type PartialDate, type PersonSummary } from '@/api/endpoints/persons.ts';

/**
 * Rendering the parts of a person a family recognizes.
 *
 * Pure and framework-free (doc 09 §4.3): no React, no AntD, so these are the first things an
 * automated frontend suite would cover without any restructuring.
 */

/**
 * A partial date, at the precision it actually has.
 *
 * Never invents the missing parts. "1901" stays "1901" rather than becoming "1 January 1901", and a
 * `circa` year is prefixed so nobody reads a recollection as a record. The âm lịch reading, when the
 * book has one, is appended verbatim — the product does not convert lunar dates (see `PartialDate`
 * on the server) — and when it is all the book has, it is shown on its own, marked as lunar.
 */
export function formatPartialDate(date: PartialDate | undefined, t: TFunction): string | undefined {
  if (date === undefined) {
    return undefined;
  }
  if (date.precision === 'UNKNOWN' || date.year === undefined) {
    // No Gregorian reading — but the book may still say "giỗ ngày mồng 3 tháng Chạp", and for an
    // ancestor whose year nobody remembers that is the whole of what is known. Showing nothing
    // here is what made such a date look as if it had never been entered.
    return date.lunarText === undefined || date.lunarText === ''
      ? undefined
      : t('tree.date.format.lunarOnly', { lunar: date.lunarText });
  }

  let base: string;
  if (date.precision === 'RANGE' && date.rangeEndYear !== undefined) {
    base = t('tree.date.format.range', { from: date.year, to: date.rangeEndYear });
  } else if (date.precision === 'EXACT' && date.month !== undefined && date.day !== undefined) {
    base = t('tree.date.format.exact', {
      day: date.day,
      month: date.month,
      year: date.year,
    });
  } else if (date.precision === 'MONTH' && date.month !== undefined) {
    base = t('tree.date.format.month', { month: date.month, year: date.year });
  } else {
    base = String(date.year);
  }

  const withCirca = date.circa ? t('tree.date.format.circa', { date: base }) : base;
  return date.lunarText === undefined || date.lunarText === ''
    ? withCirca
    : t('tree.date.format.withLunar', { date: withCirca, lunar: date.lunarText });
}

/** "1901 – 1968", or just one end, or nothing at all. The line under a name in every tree render. */
export function formatLifespan(person: PersonSummary, t: TFunction): string | undefined {
  const birth = formatPartialDate(person.birth, t);
  const death = formatPartialDate(person.death, t);
  if (birth === undefined && death === undefined) return undefined;
  if (birth !== undefined && death !== undefined) {
    return t('tree.lifespan.both', { birth, death });
  }
  if (birth !== undefined) return t('tree.lifespan.birthOnly', { birth });
  return t('tree.lifespan.deathOnly', { death });
}

/**
 * The lifespan without the lunar readings that accompany a Gregorian date.
 *
 * For the places with one short line to spend — a card on the canvas. "khoảng 1850 – 1921" is what
 * has to survive there; "(ngày rằm tháng Giêng năm Tân Dậu)" is detail the drawer shows in full.
 * A date that is *only* a lunar reading keeps it, because then the reading is the date.
 */
export function formatCompactLifespan(person: PersonSummary, t: TFunction): string | undefined {
  return formatLifespan(
    {
      ...person,
      ...(person.birth === undefined ? {} : { birth: withoutLunarGloss(person.birth) }),
      ...(person.death === undefined ? {} : { death: withoutLunarGloss(person.death) }),
    },
    t,
  );
}

/** Drops the lunar text from a date that also has a Gregorian reading; leaves any other date alone. */
function withoutLunarGloss(date: PartialDate): PartialDate {
  if (date.precision === 'UNKNOWN' || date.lunarText === undefined) {
    return date;
  }
  const { lunarText: _gloss, ...gregorian } = date;
  return gregorian;
}

/**
 * <i>Đời</i>, or the honest absence of one.
 *
 * Generation is advisory: an imported fragment with no path to the founding ancestor genuinely has
 * none, and rendering that as "đời 0" would put a stranger at the head of the family (phase 4 §8).
 */
export function formatGeneration(generation: number | undefined, t: TFunction): string {
  return generation === undefined
    ? t('tree.generation.unknown')
    : t('tree.generation.value', { generation });
}

/**
 * The Vietnamese kinship word for a parent, by role (F4.10).
 */
export function parentLabel(role: 'FATHER' | 'MOTHER', t: TFunction): string {
  return role === 'FATHER' ? t('tree.kin.father') : t('tree.kin.mother');
}

/** <i>Vợ</i> or <i>chồng</i>. */
export function spouseLabel(person: PersonSummary, t: TFunction): string {
  return person.gender === 'FEMALE' ? t('tree.kin.wife') : t('tree.kin.husband');
}

/** A short, human label for a person: their name, and their đời when it is known. */
export function personLabel(person: PersonSummary, t: TFunction): string {
  return person.generation === undefined
    ? person.fullName
    : t('tree.personLabel', { name: person.fullName, generation: person.generation });
}
