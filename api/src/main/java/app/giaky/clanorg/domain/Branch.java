package app.giaky.clanorg.domain;

import app.giaky.common.types.BranchId;
import app.giaky.common.types.ClanId;
import app.giaky.common.types.UserId;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A <i>chi</i> or <i>nhánh</i> — an entity inside the {@link Clan} aggregate (doc 02 §2.2).
 *
 * <p>Branches form a tree: one parent each, one root per clan, no cycles (I-C2, I-C3). The tree is
 * stored twice over — once as {@code parentBranchId}, which is the truth, and once as {@code path},
 * which is the fast answer to "is this inside that". Keeping the two consistent is I-C4, and it is
 * why re-parenting is the riskiest operation this phase ships.
 *
 * @param parentBranchId null for, and only for, the root branch (I-C2)
 * @param branchCode shareable code; nothing reads it until Phase 3 (phase 2 doc §8)
 * @param branchRootPersonId the person this branch descends from; null until Phase 4 (I-C5)
 * @param path materialized ancestry, always consistent with {@code parentBranchId} (I-C4)
 * @param depth distance from the root, capped at {@link #MAX_DEPTH} (I-C7)
 * @param archivedAt set when the branch is retired; an archived branch keeps its members and its
 *     history but accepts nothing new (I-C6)
 */
public record Branch(
        BranchId id,
        ClanId clanId,
        BranchId parentBranchId,
        BranchCode branchCode,
        String name,
        String description,
        UUID branchRootPersonId,
        MaterializedPath path,
        int depth,
        Instant archivedAt,
        long version,
        UserId createdBy,
        UserId updatedBy,
        Instant createdAt,
        Instant updatedAt,
        Instant deletedAt) {

    /** I-C7, and {@code ck_branches_depth} in V4. Ten is deep enough for any real <i>gia phả</i>. */
    public static final int MAX_DEPTH = 10;

    /** {@code branches.name} is {@code VARCHAR(200)}. */
    public static final int NAME_MAX_LENGTH = 200;

    public static final int DESCRIPTION_MAX_LENGTH = 2_000;

    public Branch {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(clanId, "clanId must not be null");
        Objects.requireNonNull(branchCode, "branchCode must not be null");
        Objects.requireNonNull(path, "path must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");
        name = requireName(name);
        description = Clan.trimToNull(description, DESCRIPTION_MAX_LENGTH, "description");
        requireDepth(depth);
        // I-C2 and I-C4 in one line: the root is the only branch at depth 0, and depth is the
        // number of separators in the path. A branch that fails this is a branch whose two
        // representations of the tree disagree, which is the bug the invariant exists to catch.
        if ((parentBranchId == null) != (depth == 0)) {
            throw new IllegalArgumentException(
                    "only the root branch has no parent, and only the root branch is at depth 0");
        }
        if (path.depth() != depth) {
            throw new IllegalArgumentException(
                    "path " + path + " does not agree with depth " + depth);
        }
        if (version < 0) {
            throw new IllegalArgumentException("version must not be negative");
        }
    }

    /** The root branch a clan is created with — the whole family, before anyone divides it. */
    public static Branch createRoot(
            BranchId id,
            ClanId clanId,
            BranchCode branchCode,
            String name,
            String description,
            UserId createdBy,
            Instant now) {

        return new Branch(
                id,
                clanId,
                null,
                branchCode,
                name,
                description,
                null,
                MaterializedPath.root(id),
                0,
                null,
                0L,
                createdBy,
                createdBy,
                now,
                now,
                null);
    }

    /**
     * A branch under {@code parent}.
     *
     * <p>Takes the parent rather than a parent id so the path and the depth are derived here, in one
     * place, from the one thing that determines them. A caller that could pass its own path could
     * pass a wrong one.
     *
     * @throws IllegalArgumentException the parent is archived, in another clan, or already at the
     *     depth limit
     */
    public static Branch createUnder(
            BranchId id,
            Branch parent,
            BranchCode branchCode,
            String name,
            String description,
            UserId createdBy,
            Instant now) {

        Objects.requireNonNull(parent, "parent must not be null");
        if (parent.isArchived()) {
            throw new IllegalArgumentException("cannot create a branch under an archived branch");
        }
        int depth = parent.depth() + 1;
        requireDepth(depth);

        return new Branch(
                id,
                parent.clanId(),
                parent.id(),
                branchCode,
                name,
                description,
                null,
                parent.path().child(id),
                depth,
                null,
                0L,
                createdBy,
                createdBy,
                now,
                now,
                null);
    }

    public boolean isRoot() {
        return parentBranchId == null;
    }

    public boolean isArchived() {
        return archivedAt != null;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    /** Rename and re-describe (FR-C5). Structure is {@link #reparentedTo}, not this. */
    public Branch update(String newName, String newDescription, UserId editor, Instant now) {
        return new Branch(
                id,
                clanId,
                parentBranchId,
                branchCode,
                newName == null ? name : newName,
                newDescription == null ? description : newDescription,
                branchRootPersonId,
                path,
                depth,
                archivedAt,
                version,
                createdBy,
                editor,
                createdAt,
                now,
                deletedAt);
    }

    /**
     * Names the person this branch descends from (I-C5).
     *
     * <p>Deferred from Phase 2 because {@code persons} did not exist; V10 adds the foreign key and
     * Phase 4's {@code BRANCH_ROOT} placement is what fills it in. It is not decoration: doc 04 §5.4
     * decides whether a {@code SUBTREE} grant covers a whole branch by asking whether that branch's
     * root person is inside the grant, so an unset root person is a branch no subtree grant reaches.
     *
     * @param personId the person, or null to clear the association
     */
    public Branch withRootPerson(UUID personId, UserId editor, Instant now) {
        if (Objects.equals(branchRootPersonId, personId)) {
            return this;
        }
        return new Branch(
                id,
                clanId,
                parentBranchId,
                branchCode,
                name,
                description,
                personId,
                path,
                depth,
                archivedAt,
                version,
                createdBy,
                editor,
                createdAt,
                now,
                deletedAt);
    }

    /**
     * This branch, moved under {@code newParent} (FR-C6).
     *
     * <p>Returns only the moved branch. Its descendants keep paths that are now wrong until the
     * repository rewrites them, which is why {@code BranchPathService.move} is the only caller and
     * does both inside one transaction (I-C4).
     *
     * @throws IllegalArgumentException the new parent is in another clan, archived, this branch
     *     itself, or one of its own descendants (I-C3)
     */
    public Branch reparentedTo(Branch newParent, UserId editor, Instant now) {
        Objects.requireNonNull(newParent, "newParent must not be null");
        // Ordered from the most specific diagnosis to the least. A root branch asked to move is
        // always also "under its own descendant", and telling the caller the wrong one of those two
        // things sends them looking for a cycle that is not there.
        if (isRoot()) {
            throw new IllegalArgumentException("the root branch cannot be moved");
        }
        if (!newParent.clanId().equals(clanId)) {
            throw new IllegalArgumentException("a branch cannot move to another clan");
        }
        if (newParent.id().equals(id)) {
            throw new IllegalArgumentException("a branch cannot be its own parent");
        }
        // I-C3. The path is what makes this a comparison rather than a walk: a descendant is
        // exactly a branch whose path starts with ours.
        if (newParent.path().isWithin(path)) {
            throw new IllegalArgumentException("a branch cannot move under its own descendant");
        }
        if (newParent.isArchived()) {
            throw new IllegalArgumentException("cannot move a branch under an archived branch");
        }
        int newDepth = newParent.depth() + 1;
        requireDepth(newDepth);

        return new Branch(
                id,
                clanId,
                newParent.id(),
                branchCode,
                name,
                description,
                branchRootPersonId,
                newParent.path().child(id),
                newDepth,
                archivedAt,
                version,
                createdBy,
                editor,
                createdAt,
                now,
                deletedAt);
    }

    /**
     * Retires the branch (FR-C7).
     *
     * <p>I-C6 — "not while it has live children" — is checked by the service, which is the layer that
     * can see the children. Archiving is idempotent.
     */
    public Branch archive(UserId actor, Instant now) {
        if (isArchived()) {
            return this;
        }
        if (isRoot()) {
            throw new IllegalArgumentException("the root branch cannot be archived");
        }
        return new Branch(
                id,
                clanId,
                parentBranchId,
                branchCode,
                name,
                description,
                branchRootPersonId,
                path,
                depth,
                now,
                version,
                createdBy,
                actor,
                createdAt,
                now,
                deletedAt);
    }

    /** How deep a child of this branch would sit — the number a create has to check against the cap. */
    public int childDepth() {
        return depth + 1;
    }

    private static void requireDepth(int value) {
        if (value < 0) {
            throw new IllegalArgumentException("depth must not be negative");
        }
        if (value > MAX_DEPTH) {
            throw new IllegalArgumentException(
                    "branch depth must be at most " + MAX_DEPTH + " (I-C7)");
        }
    }

    private static String requireName(String value) {
        if (value == null) {
            throw new IllegalArgumentException("name must not be null");
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        if (trimmed.length() > NAME_MAX_LENGTH) {
            throw new IllegalArgumentException(
                    "name must be at most " + NAME_MAX_LENGTH + " characters");
        }
        return trimmed;
    }
}
