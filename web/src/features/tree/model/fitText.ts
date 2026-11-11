/**
 * Fitting a line of text into a card of fixed width (FR-T1, phase 5 §8).
 *
 * SVG `<text>` neither wraps nor ellipsizes, and a tree card is 176 px wide whatever is written on
 * it. Cutting at a fixed number of characters — what the card did, for the name only — is wrong in
 * both directions: "Lý Thị Lili" is far narrower than "Đoàn Thị Ngọc Ánh Dương" at the same
 * length, and the lifespan line was not cut at all, so a date carrying its lunar reading ran a full
 * card's width past the edge and over the neighbouring node.
 *
 * Measuring with the DOM would be exact and would cost a layout per card per render, on a canvas
 * budgeted for 5,000 of them. This estimates instead: each character is given an advance width as a
 * fraction of the font size, by rough class. It errs on the wide side, so a line that fits by this
 * reckoning fits on screen, at the price of an occasional ellipsis that was not strictly needed.
 */

const ELLIPSIS = '…';

/**
 * Advance widths as fractions of the font size.
 *
 * Calibrated against the two faces the card uses (the serif name at 14 px semibold, the sans meta
 * line at 11 px), taking the wider of the two measurements for each class and rounding up. The
 * measured figures, for whoever retunes this: lowercase 0.56–0.66, capitals 0.63–0.70, digits
 * 0.56–0.63, "i"/"l" 0.26–0.34, "M"/"W" 0.85–1.06, an en dash 0.50–0.68, "…" 0.73–0.89.
 */
const NARROW = 0.34;
const SLIM = 0.44;
const LOWER = 0.62;
const DIGIT = 0.64;
const DASH = 0.7;
const UPPER = 0.72;
const ELLIPSIS_WIDTH = 0.9;
const WIDE = 1.06;
const SPACE = 0.3;

const NARROW_CHARS = new Set(['i', 'j', 'l', 'I', '.', ',', ':', ';', "'", '’', '!', '|']);
const SLIM_CHARS = new Set(['f', 't', 'r', '(', ')', '[', ']', '/']);
const DASH_CHARS = new Set(['-', '–', '—']);
const WIDE_CHARS = new Set(['m', 'w', 'M', 'W', '@', '%']);

/** A combining mark: it sits on the letter before it and takes no room of its own. */
const COMBINING = /\p{M}/u;

/** One character's estimated advance, as a fraction of the font size. */
function advanceOf(character: string): number {
  if (character === ' ') return SPACE;
  if (character === ELLIPSIS) return ELLIPSIS_WIDTH;
  if (COMBINING.test(character)) return 0;
  // Vietnamese stacks diacritics on Latin letters without widening them: "ệ" is as wide as "e".
  // "đ"/"Đ" do not decompose, and are as wide as "d"/"D".
  const base = (character.normalize('NFD')[0] ?? character).replace('đ', 'd').replace('Đ', 'D');
  if (NARROW_CHARS.has(base)) return NARROW;
  if (SLIM_CHARS.has(base)) return SLIM;
  if (DASH_CHARS.has(base)) return DASH;
  if (WIDE_CHARS.has(base)) return WIDE;
  if (base >= '0' && base <= '9') return DIGIT;
  if (base >= 'A' && base <= 'Z') return UPPER;
  if (base >= 'a' && base <= 'z') return LOWER;
  // Anything else — CJK, emoji, symbols — is assumed to take a full square and a bit.
  return 1.1;
}

/** The estimated width of `value`, in pixels, at `fontSize`. */
export function estimateTextWidth(value: string, fontSize: number): number {
  let width = 0;
  for (const character of value) width += advanceOf(character);
  return width * fontSize;
}

/**
 * `value`, or as much of it as fits in `maxWidth` followed by an ellipsis.
 *
 * Cuts between letters — never between a letter and its accents — and does not leave a space
 * dangling before the ellipsis.
 */
export function fitText(value: string, maxWidth: number, fontSize: number): string {
  if (estimateTextWidth(value, fontSize) <= maxWidth) {
    return value;
  }
  return `${prefixThatFits(value, maxWidth, fontSize).trimEnd()}${ELLIPSIS}`;
}

/**
 * Like {@link fitText}, but cuts between words.
 *
 * For a line made of tokens that mean something whole — a lifespan is years and dashes — where
 * "khoảng 1882 – 195…" is worse than saying less: it reads as a year somebody only half knew. The
 * cut steps back to the last complete word instead. A single word too long for the room has no
 * such boundary and is cut where it must be.
 */
export function fitWords(value: string, maxWidth: number, fontSize: number): string {
  if (estimateTextWidth(value, fontSize) <= maxWidth) {
    return value;
  }
  const kept = prefixThatFits(value, maxWidth, fontSize);
  const composed = value.normalize('NFC');
  const cutsInsideAWord = kept.length < composed.length && composed.charAt(kept.length) !== ' ';
  const lastSpace = kept.lastIndexOf(' ');
  const whole = cutsInsideAWord && lastSpace > 0 ? kept.slice(0, lastSpace) : kept;
  return `${whole.trimEnd()}${ELLIPSIS}`;
}

/** The longest prefix of `value` that leaves room for an ellipsis after it. */
function prefixThatFits(value: string, maxWidth: number, fontSize: number): string {
  const budget = maxWidth - estimateTextWidth(ELLIPSIS, fontSize);
  let kept = '';
  let width = 0;
  // Composed first, so "ệ" is one unit to keep or drop rather than a letter and two marks.
  for (const character of value.normalize('NFC')) {
    const next = width + advanceOf(character) * fontSize;
    if (next > budget) break;
    kept += character;
    width = next;
  }
  return kept;
}

/**
 * A person's name, shortened the way a Vietnamese reader would shorten it.
 *
 * Cutting a name at the end keeps the wrong half. Vietnamese names run family name first and given
 * name last, and in a clan nearly everybody shares the first two words: "Nguyễn Phúc Tiểu Chi" and
 * "Nguyễn Phúc Bảo Long" both cut to "Nguyễn Phúc…", which tells two cousins apart by nothing. So
 * the leading words give way first, to initials, one at a time — "N. Phúc Tiểu Chi", then
 * "N.P. Tiểu Chi" — and the given name is the last thing touched. Only a name that is still too
 * long with every other word reduced to a letter falls back to an ellipsis.
 *
 * The full name is never lost: the card carries it as its tooltip and its accessible label.
 */
export function fitName(fullName: string, maxWidth: number, fontSize: number): string {
  const words = fullName.trim().split(/\s+/).filter(Boolean);
  const whole = words.join(' ');
  if (estimateTextWidth(whole, fontSize) <= maxWidth) {
    return whole;
  }
  for (let initials = 1; initials < words.length; initials++) {
    const shortened = `${words
      .slice(0, initials)
      .map(initialOf)
      .join('')} ${words.slice(initials).join(' ')}`;
    if (estimateTextWidth(shortened, fontSize) <= maxWidth) {
      return shortened;
    }
  }
  // One enormous word, or a given name wider than the card: nothing left to abbreviate.
  const last = words.at(-1) ?? '';
  const leading = words.slice(0, -1).map(initialOf).join('');
  return fitText(leading === '' ? last : `${leading} ${last}`, maxWidth, fontSize);
}

/** "Nguyễn" → "N." — the first letter with its accents, capitalised the Vietnamese way. */
function initialOf(word: string): string {
  const [first = ''] = word.normalize('NFC');
  return `${first.toLocaleUpperCase('vi')}.`;
}

/**
 * The first of `candidates` that fits, or the last one cut to fit.
 *
 * For a line that has a fuller and a terser way of saying the same thing: listed longest first, the
 * card shows as much as it has room for, and only resorts to an ellipsis when even the tersest form
 * is too long — and then between words. Cutting the fullest form instead is how a lifespan came to
 * read "khoảng 1850 – 192…": a year sliced in half to make room for a lunar gloss that was then cut
 * off anyway.
 */
export function fitFirst(
  candidates: readonly (string | undefined)[],
  maxWidth: number,
  fontSize: number,
): string | undefined {
  const offered = candidates.filter((candidate) => candidate !== undefined);
  const fitting = offered.find((candidate) => estimateTextWidth(candidate, fontSize) <= maxWidth);
  if (fitting !== undefined) {
    return fitting;
  }
  const tersest = offered.at(-1);
  return tersest === undefined ? undefined : fitWords(tersest, maxWidth, fontSize);
}
