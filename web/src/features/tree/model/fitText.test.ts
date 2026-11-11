import { describe, expect, it } from 'vitest';

import { estimateTextWidth, fitFirst, fitName, fitText, fitWords } from './fitText';

/** The room a card has for its text: 176 px wide, text starting at x = 52, 4 px clear on the right. */
const ROOM = 120;

describe('fitText', () => {
  it('leaves a line that fits exactly as it is', () => {
    expect(fitText('Nguyễn Văn An', ROOM, 14)).toBe('Nguyễn Văn An');
    expect(fitText('1901 – 1968', ROOM, 11)).toBe('1901 – 1968');
    expect(fitText('', ROOM, 11)).toBe('');
  });

  it('cuts a long name and says so with an ellipsis', () => {
    const fitted = fitText('Đoàn Thị Ngọc Ánh Dương Minh Châu', ROOM, 14);

    expect(fitted).toBe('Đoàn Thị Ngọc…');
    expect(estimateTextWidth(fitted, 14)).toBeLessThanOrEqual(ROOM);
  });

  it('cuts the lifespan line too — the one that used to run a card-width past the edge', () => {
    const lifespan = '7/1943 (tháng 6 năm Quý Mùi) – 2/1968 (tháng 2 năm Mậu Thân)';

    const fitted = fitText(lifespan, ROOM, 11);

    expect(fitted).not.toBe(lifespan);
    expect(fitted.endsWith('…')).toBe(true);
    expect(estimateTextWidth(fitted, 11)).toBeLessThanOrEqual(ROOM);
  });

  it('never returns anything wider than the room, whatever it is given', () => {
    const awkward = [
      'WWWWWWWWWWWWWWWWWWWWWWWWWWWWWW',
      'iiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiii',
      'Tôn Nữ Thị Ánh Tuyết Hoàng Cung Đại Nội',
      '阮福映阮福映阮福映阮福映阮福映',
      'a'.repeat(500),
    ];

    for (const value of awkward) {
      expect(estimateTextWidth(fitText(value, ROOM, 14), 14), value).toBeLessThanOrEqual(ROOM);
    }
  });

  it('keeps more of a narrow name than of a wide one of the same length', () => {
    const narrow = fitText('l'.repeat(60), ROOM, 14);
    const wide = fitText('M'.repeat(60), ROOM, 14);

    expect(narrow.length).toBeGreaterThan(wide.length);
  });

  it('does not leave a space hanging before the ellipsis', () => {
    // Narrow enough that the cut lands right after a word.
    const fitted = fitText('Nguyễn Phúc Tiểu Chi', 75, 14);

    expect(fitted).toMatch(/\S…$/);
  });

  it('does not split an accented letter from its accents', () => {
    // Decomposed input: each "ệ" is three code points.
    const decomposed = 'Việt '.normalize('NFD').repeat(12);

    const fitted = fitText(decomposed, ROOM, 14).normalize('NFC');

    expect(fitted).toMatch(/^(Việt ?)+(V|Vi|Việ|Việt)?…$/);
    expect(fitted).not.toMatch(/Vie|Viê|Viẹ/);
  });
});

describe('estimateTextWidth', () => {
  it('counts a Vietnamese letter as its base letter, however many marks it carries', () => {
    expect(estimateTextWidth('ệ', 14)).toBe(estimateTextWidth('e', 14));
    expect(estimateTextWidth('Đ', 14)).toBe(estimateTextWidth('D', 14));
    expect(estimateTextWidth('Nguyễn', 14)).toBe(estimateTextWidth('Nguyen', 14));
  });

  it('gives a combining mark no width of its own', () => {
    expect(estimateTextWidth('Việt'.normalize('NFD'), 14)).toBe(estimateTextWidth('Việt', 14));
  });

  it('is never narrower than the text really is', () => {
    // Measured in the browser, in the card's own fonts (px): the serif name at 14, the meta at 11.
    const measuredName: [string, number][] = [
      ['Nguyễn Văn An', 106.67],
      ['Đoàn Thị Ngọc Ánh Dương', 183.41],
      ['Tôn Nữ Thị Ánh Tuyết', 152.53],
      ['Nguyễn Phúc Tiểu Chi', 151.67],
      ['Lý Thị Lili', 69.94],
    ];
    const measuredMeta: [string, number][] = [
      ['1901 – 1968', 62.01],
      ['khoảng 1890 – 1968', 107.34],
      ['7/1943 (tháng 6 năm Quý Mùi)', 163.36],
      ['mất mồng 3 tháng Chạp (âm lịch)', 180.8],
    ];

    for (const [text, real] of measuredName) {
      expect(estimateTextWidth(text, 14), text).toBeGreaterThanOrEqual(real);
    }
    for (const [text, real] of measuredMeta) {
      expect(estimateTextWidth(text, 11), text).toBeGreaterThanOrEqual(real);
    }
  });

  it('scales with the font size', () => {
    expect(estimateTextWidth('Chi Hai', 22)).toBeCloseTo(estimateTextWidth('Chi Hai', 11) * 2);
  });
});

describe('fitName', () => {
  it('leaves a name that fits alone, tidying stray spaces', () => {
    expect(fitName('Nguyễn Văn An', ROOM, 14)).toBe('Nguyễn Văn An');
    expect(fitName('  Nguyễn   Văn An ', ROOM, 14)).toBe('Nguyễn Văn An');
  });

  it('gives up the family name before the given name', () => {
    expect(fitName('Nguyễn Phúc Tiểu Chi', ROOM, 14)).toBe('N. Phúc Tiểu Chi');
  });

  it('abbreviates only as much as it has to, one more word at a time', () => {
    expect(fitName('Nguyễn Phúc Tiểu Chi', 160, 14)).toBe('Nguyễn Phúc Tiểu Chi');
    expect(fitName('Nguyễn Phúc Tiểu Chi', 100, 14)).toBe('N.P. Tiểu Chi');
    expect(fitName('Nguyễn Phúc Tiểu Chi', 80, 14)).toBe('N.P.T. Chi');
  });

  it('keeps two cousins who share a family name apart', () => {
    const one = fitName('Nguyễn Phúc Tiểu Chi', ROOM, 14);
    const other = fitName('Nguyễn Phúc Bảo Long', ROOM, 14);

    expect(one).not.toBe(other);
    expect(one.endsWith('Tiểu Chi')).toBe(true);
    expect(other.endsWith('Bảo Long')).toBe(true);
  });

  it('reduces every word but the last when it must', () => {
    expect(fitName('Đoàn Thị Ngọc Ánh Dương', ROOM, 14)).toBe('Đ.T.N.Á. Dương');
  });

  it('keeps the accents of an initial', () => {
    expect(fitName('Ánh Ngọc Thị Đoàn Dương Minh Châu', ROOM, 14).startsWith('Á.')).toBe(true);
  });

  it('falls back to an ellipsis only when the given name itself does not fit', () => {
    const fitted = fitName('Nguyễn Thị Supercalifragilisticexpialidocious', ROOM, 14);

    expect(fitted.startsWith('N.T. Supercali')).toBe(true);
    expect(fitted.endsWith('…')).toBe(true);
    expect(fitName('Supercalifragilisticexpialidocious', ROOM, 14).endsWith('…')).toBe(true);
  });

  it('never returns anything wider than the room', () => {
    const names = [
      'Nguyễn Phúc Tiểu Chi',
      'Đoàn Thị Ngọc Ánh Dương',
      'Huỳnh Thị Thuỷ Tiên',
      'Công Tằng Tôn Nữ Thị Minh Nguyệt Hoàng Lan',
      'WWWW MMMM WWWW MMMMMMMMMMMMMM',
      '',
    ];

    for (const name of names) {
      expect(estimateTextWidth(fitName(name, ROOM, 14), 14), name).toBeLessThanOrEqual(ROOM);
    }
  });
});

describe('fitFirst', () => {
  const full = 'khoảng 1850 – 1921 (ngày rằm tháng Giêng năm Tân Dậu)';
  const compact = 'khoảng 1850 – 1921';

  it('shows the fullest form when there is room for it', () => {
    expect(fitFirst([full, compact], 400, 11)).toBe(full);
  });

  it('falls back to the terser form rather than cutting the fuller one', () => {
    // The line that used to come out as "khoảng 1850 – 192…".
    expect(fitFirst([full, compact], ROOM, 11)).toBe(compact);
  });

  it('cuts the tersest form only when nothing fits, and then between words', () => {
    const fitted = fitFirst([full, compact], 100, 11);

    expect(fitted).toBe('khoảng 1850 –…');
    expect(estimateTextWidth(fitted ?? '', 11)).toBeLessThanOrEqual(100);
  });

  it('skips forms that do not exist, and is undefined when none does', () => {
    expect(fitFirst([undefined, compact], ROOM, 11)).toBe(compact);
    expect(fitFirst([undefined, undefined], ROOM, 11)).toBeUndefined();
    expect(fitFirst([], ROOM, 11)).toBeUndefined();
  });
});

describe('fitWords', () => {
  it('never shows half a year', () => {
    // A range of death years after an approximate birth: too long for a card even without glosses.
    const fitted = fitWords('khoảng 1882 – 1950–1955', ROOM, 11);

    expect(fitted).toBe('khoảng 1882 –…');
    expect(fitted).not.toMatch(/\d…$/);
  });

  it('leaves a line that fits alone', () => {
    expect(fitWords('1901 – 1968', ROOM, 11)).toBe('1901 – 1968');
  });

  it('keeps every whole word there is room for', () => {
    expect(fitWords('Chi Hai — nhánh Út (Quảng Nam)', 106, 10)).toBe('Chi Hai — nhánh Út…');
  });

  it('cuts inside a word only when the word alone is too long', () => {
    const fitted = fitWords('Supercalifragilisticexpialidocious', ROOM, 11);

    expect(fitted.endsWith('…')).toBe(true);
    expect(fitted.length).toBeGreaterThan(5);
    expect(estimateTextWidth(fitted, 11)).toBeLessThanOrEqual(ROOM);
  });
});
