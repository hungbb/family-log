/**
 * Turning a drawn tree into a file (#61).
 *
 * The tree is styled by styled-components, whose rules live in the page's stylesheet — an SVG
 * copied out of the page on its own would arrive with no colours, no fonts and no dashes. So the
 * export walks the drawn SVG and its copy side by side and writes each element's *computed* styles
 * onto the copy as inline styles, which also resolves every `var(--tree-*)` to a real colour. The
 * result is a self-contained vector file; the PNG is that file rasterised.
 */

/** The properties the tree's styles actually set. Anything else is the browser default anyway. */
const STYLED = [
  'fill',
  'fill-opacity',
  'stroke',
  'stroke-width',
  'stroke-dasharray',
  'stroke-linecap',
  'stroke-linejoin',
  'stroke-opacity',
  'opacity',
  'font-family',
  'font-size',
  'font-weight',
  'letter-spacing',
  'text-anchor',
  'dominant-baseline',
  'text-transform',
  'paint-order',
  'vector-effect',
  'visibility',
] as const;

/** A standalone SVG document of `source`, with its styles inlined. */
export function serializeSvg(source: SVGSVGElement): string {
  const copy = source.cloneNode(true) as SVGSVGElement;
  const originals = [source, ...source.querySelectorAll('*')];
  const copies = [copy, ...copy.querySelectorAll('*')];
  originals.forEach((original, index) => {
    const target = copies[index];
    if (!(target instanceof Element)) return;
    const computed = window.getComputedStyle(original);
    const declarations = STYLED.map((property) => {
      const value = computed.getPropertyValue(property);
      return value === '' ? '' : `${property}:${value}`;
    })
      .filter((declaration) => declaration !== '')
      .join(';');
    target.setAttribute('style', declarations);
    target.removeAttribute('class');
  });
  copy.setAttribute('xmlns', 'http://www.w3.org/2000/svg');
  return `<?xml version="1.0" encoding="UTF-8"?>\n${new XMLSerializer().serializeToString(copy)}`;
}

/**
 * The largest canvas side every current browser will allocate. Beyond it `toBlob` silently yields
 * nothing, so a very wide clan is rasterised at a lower density instead.
 */
const MAX_CANVAS_SIDE = 16_384;
const MAX_CANVAS_AREA = 120_000_000;

/** Rasterises an SVG document at `density` device pixels per SVG pixel, on `background`. */
export async function rasterize(
  svg: string,
  width: number,
  height: number,
  density: number,
  background: string,
): Promise<Blob> {
  const fit = Math.min(
    density,
    MAX_CANVAS_SIDE / width,
    MAX_CANVAS_SIDE / height,
    Math.sqrt(MAX_CANVAS_AREA / (width * height)),
  );
  const image = new Image();
  image.decoding = 'async';
  const loaded = new Promise<void>((resolve, reject) => {
    image.onload = () => {
      resolve();
    };
    image.onerror = () => {
      reject(new Error('The tree could not be rendered as an image.'));
    };
  });
  image.src = `data:image/svg+xml;charset=utf-8,${encodeURIComponent(svg)}`;
  await loaded;

  const canvas = document.createElement('canvas');
  canvas.width = Math.max(1, Math.floor(width * fit));
  canvas.height = Math.max(1, Math.floor(height * fit));
  const context = canvas.getContext('2d');
  if (context === null) throw new Error('No 2D canvas is available.');
  context.fillStyle = background;
  context.fillRect(0, 0, canvas.width, canvas.height);
  context.drawImage(image, 0, 0, canvas.width, canvas.height);

  return new Promise<Blob>((resolve, reject) => {
    canvas.toBlob((blob) => {
      if (blob === null) reject(new Error('The image was too large to encode.'));
      else resolve(blob);
    }, 'image/png');
  });
}

/** Hands a file to the browser's download. */
export function saveBlob(blob: Blob, filename: string): void {
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = filename;
  document.body.append(link);
  link.click();
  link.remove();
  // Revoked on the next task: Safari cancels a download whose URL is revoked in the same one.
  window.setTimeout(() => {
    URL.revokeObjectURL(url);
  }, 0);
}

/** A filename-safe version of a clan's name: no diacritics, no spaces, nothing a filesystem minds. */
export function fileNameOf(title: string, extension: string): string {
  const base = title
    .normalize('NFD')
    .replace(/[̀-ͯ]/g, '')
    .replace(/đ/g, 'd')
    .replace(/Đ/g, 'D')
    .replace(/[^A-Za-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '')
    .toLowerCase();
  return `${base === '' ? 'gia-pha' : base}.${extension}`;
}
