import { useMemo } from 'react';

import { type Tree } from '@/api/endpoints/tree.ts';

import { buildGraph, type TreeGraph } from '../model/buildGraph';
import { layoutTree, type TreeLayout } from '../model/layout';
import { useTreeViewStore } from '../stores/useTreeViewStore';

/**
 * The payload, normalized and laid out (F5.1, F5.2).
 *
 * **Two memos, not one**, and the split is the point. `buildGraph` depends only on the payload, so
 * collapsing a subtree or turning the tree on its side does not re-normalize five thousand rows —
 * it re-runs the layout over an index that is already built. Merging them would make every
 * interaction pay for the whole pipeline.
 */
export function useTreeLayout(tree: Tree | undefined): {
  graph: TreeGraph | null;
  layout: TreeLayout;
} {
  const orientation = useTreeViewStore((state) => state.orientation);
  const collapsed = useTreeViewStore((state) => state.collapsed);

  const graph = useMemo(() => (tree === undefined ? null : buildGraph(tree)), [tree]);

  const layout = useMemo(
    () => layoutTree(graph?.roots ?? [], { orientation, collapsed }),
    [graph, orientation, collapsed],
  );

  return { graph, layout };
}
