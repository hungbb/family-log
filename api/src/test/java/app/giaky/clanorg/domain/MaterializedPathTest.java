package app.giaky.clanorg.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.giaky.common.types.BranchId;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** {@link MaterializedPath}: the string that has to agree with the tree (I-C4). */
class MaterializedPathTest {

    private static final BranchId ROOT =
            BranchId.of(UUID.fromString("a1b2c3d4-0000-0000-0000-000000000001"));
    private static final BranchId CHILD =
            BranchId.of(UUID.fromString("9f8e7d6c-0000-0000-0000-000000000002"));
    private static final BranchId GRANDCHILD =
            BranchId.of(UUID.fromString("11223344-0000-0000-0000-000000000003"));

    @Nested
    @DisplayName("construction")
    class Construction {

        @Test
        @DisplayName("a root path is one segment, slashed at both ends")
        void rootPath() {
            assertThat(MaterializedPath.root(ROOT).value()).isEqualTo("/a1b2c3d4/");
        }

        @Test
        @DisplayName("a child path appends its own segment")
        void childPath() {
            MaterializedPath child = MaterializedPath.root(ROOT).child(CHILD);
            assertThat(child.value()).isEqualTo("/a1b2c3d4/9f8e7d6c/");
        }

        @Test
        @DisplayName("depth is the number of ancestors, so a root sits at zero")
        void depthCountsAncestors() {
            MaterializedPath root = MaterializedPath.root(ROOT);
            assertThat(root.depth()).isZero();
            assertThat(root.child(CHILD).depth()).isEqualTo(1);
            assertThat(root.child(CHILD).child(GRANDCHILD).depth()).isEqualTo(2);
        }

        @Test
        @DisplayName("segments come back root first")
        void segmentsAreOrdered() {
            assertThat(MaterializedPath.root(ROOT).child(CHILD).segments())
                    .containsExactly("a1b2c3d4", "9f8e7d6c");
        }

        @Test
        @DisplayName("a path without its slashes is refused")
        void slashesAreRequired() {
            assertThatThrownBy(() -> MaterializedPath.of("a1b2c3d4/"))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> MaterializedPath.of("/a1b2c3d4"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("a path longer than the column is refused")
        void lengthIsCapped() {
            String tooLong = "/" + "a".repeat(MaterializedPath.MAX_LENGTH) + "/";
            assertThatThrownBy(() -> MaterializedPath.of(tooLong))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("ancestry")
    class Ancestry {

        private final MaterializedPath root = MaterializedPath.root(ROOT);
        private final MaterializedPath child = root.child(CHILD);
        private final MaterializedPath grandchild = child.child(GRANDCHILD);

        @Test
        @DisplayName("a branch is within itself, which is what a subtree scope means")
        void isWithinIsReflexive() {
            assertThat(child.isWithin(child)).isTrue();
            assertThat(child.isStrictDescendantOf(child)).isFalse();
        }

        @Test
        @DisplayName("descendants are within their ancestors, and not the other way round")
        void descendantsAreWithinAncestors() {
            assertThat(grandchild.isWithin(root)).isTrue();
            assertThat(grandchild.isStrictDescendantOf(child)).isTrue();
            assertThat(root.isWithin(grandchild)).isFalse();
        }

        @Test
        @DisplayName("the trailing slash stops a longer segment from matching a shorter one")
        void trailingSlashPreventsPrefixCollisions() {
            // Without the terminating slash, "/a1b2c3d4" would prefix "/a1b2c3d40000/" and a
            // descendant query would quietly return another branch's subtree.
            MaterializedPath sibling = MaterializedPath.of("/a1b2c3d40000/");
            assertThat(sibling.isWithin(root)).isFalse();
        }

        @Test
        @DisplayName("the LIKE pattern matches the branch and everything under it")
        void subtreePattern() {
            assertThat(child.subtreeLikePattern()).isEqualTo("/a1b2c3d4/9f8e7d6c/%");
        }
    }
}
