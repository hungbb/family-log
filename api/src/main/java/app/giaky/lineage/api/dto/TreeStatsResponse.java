package app.giaky.lineage.api.dto;

import app.giaky.lineage.application.TreeStatsView;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

/**
 * Counts for the tree overview (doc 05 §4.9).
 *
 * @param generationCounts one entry per <i>đời</i>, ascending. The entry whose {@code generation} is
 *     null counts the people whose generation is unknown — a number worth showing rather than hiding,
 *     because it is the size of the work still to do on the book
 */
@Schema(description = "Counts by generation, life status and branch.")
public record TreeStatsResponse(
        long personCount,
        long livingCount,
        long deceasedCount,
        long unlinkedCount,
        Integer maxGeneration,
        List<GenerationCountResponse> generationCounts,
        List<BranchCountResponse> branchCounts) {

    public record GenerationCountResponse(Integer generation, long count) {}

    public record BranchCountResponse(UUID branchId, long count) {}

    public static TreeStatsResponse of(TreeStatsView view) {
        return new TreeStatsResponse(
                view.personCount(),
                view.livingCount(),
                view.deceasedCount(),
                view.unlinkedCount(),
                view.maxGeneration(),
                view.generationCounts().stream()
                        .map(row -> new GenerationCountResponse(row.generation(), row.count()))
                        .toList(),
                view.branchCounts().stream()
                        .map(row -> new BranchCountResponse(row.branchId(), row.count()))
                        .toList());
    }
}
