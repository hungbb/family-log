package app.giaky.clanorg.support;

import app.giaky.clanorg.domain.Branch;
import app.giaky.clanorg.domain.BranchCode;
import app.giaky.clanorg.domain.BranchRepository;
import app.giaky.clanorg.domain.MaterializedPath;
import app.giaky.common.types.BranchId;
import app.giaky.common.types.ClanId;
import app.giaky.common.types.UserId;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * A {@link BranchRepository} backed by a map.
 *
 * <p><b>{@link #rewriteSubtreePaths} is a second implementation of the recursive statement in {@code
 * BranchJpaRepository}, and that is a real risk worth naming.</b> Two implementations of one rule can
 * disagree, and the unit tests would keep passing while production was wrong. It is done anyway
 * because the property test in {@code BranchPathPropertyTest} — hundreds of random move sequences
 * checked against the parent links — is worth far more than the same test would be if it needed a
 * container per run. {@code BranchStructureIT} performs the same moves against real PostgreSQL and
 * asserts the same invariant, which is what keeps the two honest.
 *
 * <p>Both walk the tree from {@code parent_branch_id} rather than patching the old path strings, so
 * they agree by construction on the case that matters: the result is derived from the structure, not
 * from the paths it replaces.
 */
public final class InMemoryBranchRepository implements BranchRepository {

    private final Map<BranchId, Branch> byId = new LinkedHashMap<>();
    private final Set<String> issuedCodes = new HashSet<>();

    @Override
    public Optional<Branch> findById(ClanId clanId, BranchId id) {
        return Optional.ofNullable(byId.get(id))
                .filter(branch -> !branch.isDeleted())
                // The tenancy filter, mirrored: a branch id from another clan is simply absent.
                .filter(branch -> branch.clanId().equals(clanId));
    }

    @Override
    public List<Branch> findAllByClan(ClanId clanId) {
        return byId.values().stream()
                .filter(branch -> !branch.isDeleted())
                .filter(branch -> branch.clanId().equals(clanId))
                // Path order is depth-first order, as `ORDER BY path` gives in the real adapter.
                .sorted(Comparator.comparing(branch -> branch.path().value()))
                .toList();
    }

    @Override
    public Optional<Branch> findByBranchCode(BranchCode code) {
        return byId.values().stream()
                .filter(branch -> !branch.isDeleted())
                .filter(branch -> branch.branchCode().equals(code))
                .findFirst();
    }

    @Override
    public boolean isBranchCodeTaken(String code) {
        return issuedCodes.contains(code);
    }

    @Override
    public boolean isPathTaken(ClanId clanId, MaterializedPath path) {
        return byId.values().stream()
                .filter(branch -> !branch.isDeleted())
                .filter(branch -> branch.clanId().equals(clanId))
                .anyMatch(branch -> branch.path().equals(path));
    }

    @Override
    public long countLiveChildren(ClanId clanId, BranchId parentId) {
        return byId.values().stream()
                .filter(branch -> !branch.isDeleted() && !branch.isArchived())
                .filter(branch -> branch.clanId().equals(clanId))
                .filter(branch -> parentId.equals(branch.parentBranchId()))
                .count();
    }

    @Override
    public Branch save(Branch branch) {
        byId.put(branch.id(), branch);
        issuedCodes.add(branch.branchCode().value());
        return branch;
    }

    @Override
    public int rewriteSubtreePaths(ClanId clanId, BranchId root, Instant now, UserId editor) {
        Branch start = byId.get(root);
        if (start == null || !start.clanId().equals(clanId)) {
            return 0;
        }
        Map<BranchId, List<Branch>> childrenOf = childrenByParent(clanId);

        int rewritten = 0;
        Deque<Branch> queue = new ArrayDeque<>(childrenOf.getOrDefault(root, List.of()));
        Map<BranchId, Branch> current = new LinkedHashMap<>();
        current.put(start.id(), start);

        while (!queue.isEmpty()) {
            Branch child = queue.removeFirst();
            Branch parent = current.get(child.parentBranchId());
            Branch moved =
                    new Branch(
                            child.id(),
                            child.clanId(),
                            child.parentBranchId(),
                            child.branchCode(),
                            child.name(),
                            child.description(),
                            child.branchRootPersonId(),
                            parent.path().child(child.id()),
                            parent.depth() + 1,
                            child.archivedAt(),
                            child.version(),
                            child.createdBy(),
                            editor,
                            child.createdAt(),
                            now,
                            child.deletedAt());
            byId.put(moved.id(), moved);
            current.put(moved.id(), moved);
            rewritten++;
            queue.addAll(childrenOf.getOrDefault(moved.id(), List.of()));
        }
        return rewritten;
    }

    private Map<BranchId, List<Branch>> childrenByParent(ClanId clanId) {
        Map<BranchId, List<Branch>> childrenOf = new LinkedHashMap<>();
        for (Branch branch : byId.values()) {
            if (branch.isDeleted() || !branch.clanId().equals(clanId) || branch.isRoot()) {
                continue;
            }
            childrenOf.computeIfAbsent(branch.parentBranchId(), key -> new ArrayList<>()).add(branch);
        }
        return childrenOf;
    }

    /** Every branch, whatever its clan — for assertions that must see across the tenancy boundary. */
    public List<Branch> all() {
        return List.copyOf(byId.values());
    }

    public void reserveCode(String code) {
        issuedCodes.add(code);
    }
}
