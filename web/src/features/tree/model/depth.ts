import { type CoupleNode } from './buildGraph';

/**
 * Generations as the canvas draws them: levels below the roots, counted from 1.
 *
 * Used by "show N generations" (#60). It counts levels of the laid-out tree rather than recorded
 * *đời*, because what it controls is how deep the picture goes — and a fragment attached halfway
 * down has a *đời* that says nothing about how many levels sit above it on screen.
 */

/** How many levels the tree has when everything is expanded. */
export function levelCount(roots: CoupleNode[]): number {
  let deepest = 0;
  const walk = (node: CoupleNode, level: number) => {
    deepest = Math.max(deepest, level);
    for (const child of node.children) walk(child, level + 1);
  };
  for (const root of roots) walk(root, 1);
  return deepest;
}

/**
 * The collapsed set that shows exactly `levels` levels: every slot on the last visible level that
 * has children is collapsed, and nothing above it is.
 */
export function collapsedBeyond(roots: CoupleNode[], levels: number): Set<string> {
  const collapsed = new Set<string>();
  const walk = (node: CoupleNode, level: number) => {
    if (node.children.length === 0) return;
    if (level >= levels) {
      collapsed.add(node.key);
      return;
    }
    for (const child of node.children) walk(child, level + 1);
  };
  for (const root of roots) walk(root, 1);
  return collapsed;
}
