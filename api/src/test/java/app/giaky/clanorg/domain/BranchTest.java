package app.giaky.clanorg.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.giaky.common.types.BranchId;
import app.giaky.common.types.ClanId;
import app.giaky.common.types.UserId;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** {@link Branch}: the invariants that keep the branch graph a tree (I-C2, I-C3, I-C6, I-C7). */
class BranchTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private static final ClanId CLAN = ClanId.of(new UUID(0L, 1L));
    private static final ClanId OTHER_CLAN = ClanId.of(new UUID(0L, 99L));
    private static final UserId ACTOR = UserId.of(new UUID(0L, 2L));

    private int nextId = 100;

    /**
     * A branch id whose first eight hex characters are unique.
     *
     * <p>The path segment is taken from exactly those characters (doc 02 §3), so ids that differ
     * only in their low bits would all share one path and every ancestry assertion below would be
     * about a tree that could not exist.
     */
    private BranchId id() {
        return BranchId.of(new UUID(((long) nextId++) << 32, 0L));
    }

    private Branch root(ClanId clan) {
        BranchId branchId = id();
        return Branch.createRoot(branchId, clan, BranchCode.of("HO-AAAA"), "Cả họ", null, ACTOR, NOW);
    }

    private Branch under(Branch parent, String name) {
        return Branch.createUnder(id(), parent, BranchCode.of("CHI-BBBB"), name, null, ACTOR, NOW);
    }

    @Nested
    @DisplayName("creation")
    class Creation {

        @Test
        @DisplayName("the root branch has no parent and sits at depth 0 (I-C2)")
        void rootShape() {
            Branch root = root(CLAN);

            assertThat(root.isRoot()).isTrue();
            assertThat(root.parentBranchId()).isNull();
            assertThat(root.depth()).isZero();
            assertThat(root.path()).isEqualTo(MaterializedPath.root(root.id()));
        }

        @Test
        @DisplayName("a child inherits its clan and extends its parent path")
        void childShape() {
            Branch root = root(CLAN);
            Branch child = under(root, "Chi Trưởng");

            assertThat(child.clanId()).isEqualTo(CLAN);
            assertThat(child.parentBranchId()).isEqualTo(root.id());
            assertThat(child.depth()).isEqualTo(1);
            assertThat(child.path().isStrictDescendantOf(root.path())).isTrue();
        }

        @Test
        @DisplayName("nesting is allowed exactly ten deep, and no further (I-C7)")
        void depthCap() {
            Branch current = root(CLAN);
            for (int depth = 1; depth <= Branch.MAX_DEPTH; depth++) {
                current = under(current, "Đời " + depth);
                assertThat(current.depth()).isEqualTo(depth);
            }

            Branch deepest = current;
            assertThatThrownBy(() -> under(deepest, "one too many"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("at most " + Branch.MAX_DEPTH);
        }

        @Test
        @DisplayName("nothing may be created under an archived branch")
        void noChildrenUnderArchived() {
            Branch archived = under(root(CLAN), "Chi Hai").archive(ACTOR, NOW);

            assertThatThrownBy(() -> under(archived, "Nhánh An Giang"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("archived");
        }

        @Test
        @DisplayName("a name that is only whitespace is not a name")
        void nameMustNotBeBlank() {
            Branch root = root(CLAN);
            assertThatThrownBy(() -> under(root, "   "))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("a path that disagrees with the depth is refused on construction (I-C4)")
        void pathAndDepthMustAgree() {
            Branch root = root(CLAN);
            assertThatThrownBy(
                            () ->
                                    new Branch(
                                            id(),
                                            CLAN,
                                            root.id(),
                                            BranchCode.of("CHI-CCCC"),
                                            "wrong",
                                            null,
                                            null,
                                            // A depth-1 path claiming depth 2.
                                            MaterializedPath.root(root.id()).child(id()),
                                            2,
                                            null,
                                            0L,
                                            ACTOR,
                                            ACTOR,
                                            NOW,
                                            NOW,
                                            null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("does not agree with depth");
        }
    }

    @Nested
    @DisplayName("re-parenting")
    class Reparenting {

        @Test
        @DisplayName("a branch cannot move under its own descendant (I-C3)")
        void noCycles() {
            Branch root = root(CLAN);
            Branch chiHai = under(root, "Chi Hai");
            Branch anGiang = under(chiHai, "Nhánh An Giang");

            assertThatThrownBy(() -> chiHai.reparentedTo(anGiang, ACTOR, NOW))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("its own descendant");
        }

        @Test
        @DisplayName("a branch cannot become its own parent")
        void noSelfParenting() {
            Branch chi = under(root(CLAN), "Chi Hai");

            assertThatThrownBy(() -> chi.reparentedTo(chi, ACTOR, NOW))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("a branch cannot move into another clan")
        void noCrossClanMoves() {
            Branch chi = under(root(CLAN), "Chi Hai");
            Branch elsewhere = root(OTHER_CLAN);

            assertThatThrownBy(() -> chi.reparentedTo(elsewhere, ACTOR, NOW))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("another clan");
        }

        @Test
        @DisplayName("the root branch cannot be moved")
        void rootStaysPut() {
            Branch root = root(CLAN);
            Branch chi = under(root, "Chi Hai");

            assertThatThrownBy(() -> root.reparentedTo(chi, ACTOR, NOW))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("root branch cannot be moved");
        }

        @Test
        @DisplayName("nothing may move under an archived branch")
        void noMovingUnderArchived() {
            Branch root = root(CLAN);
            Branch chiMot = under(root, "Chi Một");
            Branch chiHai = under(root, "Chi Hai").archive(ACTOR, NOW);

            assertThatThrownBy(() -> chiMot.reparentedTo(chiHai, ACTOR, NOW))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("archived");
        }

        @Test
        @DisplayName("a successful move rewrites the branch path and depth")
        void moveRewritesPathAndDepth() {
            Branch root = root(CLAN);
            Branch chiMot = under(root, "Chi Một");
            Branch chiHai = under(root, "Chi Hai");

            Branch moved = chiHai.reparentedTo(chiMot, ACTOR, NOW);

            assertThat(moved.parentBranchId()).isEqualTo(chiMot.id());
            assertThat(moved.depth()).isEqualTo(2);
            assertThat(moved.path()).isEqualTo(chiMot.path().child(chiHai.id()));
            // Its identity and its code are untouched: a move is not a re-creation.
            assertThat(moved.id()).isEqualTo(chiHai.id());
            assertThat(moved.branchCode()).isEqualTo(chiHai.branchCode());
        }
    }

    @Nested
    @DisplayName("archiving")
    class Archiving {

        @Test
        @DisplayName("archiving stamps the time and is idempotent")
        void archiveIsIdempotent() {
            Branch chi = under(root(CLAN), "Chi Hai");

            Branch archived = chi.archive(ACTOR, NOW);
            assertThat(archived.isArchived()).isTrue();
            assertThat(archived.archivedAt()).isEqualTo(NOW);

            Branch again = archived.archive(ACTOR, NOW.plusSeconds(3600));
            // The second call must not move the date: when it was retired is a fact.
            assertThat(again.archivedAt()).isEqualTo(NOW);
        }

        @Test
        @DisplayName("the root branch cannot be archived — it is the clan")
        void rootCannotBeArchived() {
            Branch root = root(CLAN);
            assertThatThrownBy(() -> root.archive(ACTOR, NOW))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
