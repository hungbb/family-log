/**
 * Diacritic-insensitive comparison, matching what the server's search does (B4.11).
 *
 * "nguyen van an" has to find "Nguyễn Văn An" and so does "NGUYỄN", because somebody searching their
 * own family on a keyboard without Vietnamese input is the ordinary case and not the exception.
 * `đ` is handled separately: it is a distinct letter rather than a `d` with a mark, so
 * decomposition leaves it alone and it has to be folded by hand.
 */
export function fold(value: string): string {
  return value
    .toLocaleLowerCase('vi')
    .normalize('NFD')
    .replace(/\p{Diacritic}/gu, '')
    .replace(/đ/g, 'd');
}
