package app.giaky.lineage.application;

import java.util.List;
import java.util.Map;

/**
 * Counts for the tree overview (doc 05 §4.9).
 *
 * @param generationCounts people per <i>đời</i>, ascending; the key is null for those whose
 *     generation is not known, which is a number worth showing rather than hiding
 * @param unlinkedCount nodes with no account attached — the pool Phase 6 claims from
 */
public record TreeStatsView(
        long personCount,
        long livingCount,
        long deceasedCount,
        long unlinkedCount,
        Integer maxGeneration,
        List<GenerationCount> generationCounts,
        List<BranchCount> branchCounts) {

    /** How many live people sit at one <i>đời</i>. */
    public record GenerationCount(Integer generation, long count) {}

    /** How many live people are placed in one branch. */
    public record BranchCount(java.util.UUID branchId, long count) {}

    static TreeStatsView empty() {
        return new TreeStatsView(0, 0, 0, 0, null, List.of(), List.of());
    }

    /** Convenience for assembling {@link #generationCounts} from a map. */
    static List<GenerationCount> generationCounts(Map<Integer, Long> counts) {
        return counts.entrySet().stream()
                .sorted(
                        (left, right) -> {
                            if (left.getKey() == null) {
                                return 1;
                            }
                            if (right.getKey() == null) {
                                return -1;
                            }
                            return Integer.compare(left.getKey(), right.getKey());
                        })
                .map(entry -> new GenerationCount(entry.getKey(), entry.getValue()))
                .toList();
    }
}
