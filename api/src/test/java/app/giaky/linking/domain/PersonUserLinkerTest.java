package app.giaky.linking.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.giaky.common.error.ConflictException;
import app.giaky.common.error.DomainRuleViolation;
import app.giaky.common.error.ErrorCode;
import app.giaky.common.error.NotFoundException;
import app.giaky.common.types.BranchId;
import app.giaky.common.types.ClanId;
import app.giaky.common.types.PersonId;
import app.giaky.common.types.UserId;
import java.time.Instant;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link PersonUserLinker} against stub ports — no Spring, no database (doc 09 §4.2).
 *
 * <p>Three invariants, tested in isolation because three services depend on them being enforced in
 * exactly one place. The compare-and-set that decides a genuine race is a database statement and is
 * tested against real Postgres in {@code LinkConcurrencyIT}; what is testable here is that the
 * linker refuses the losing side when the statement says it lost.
 */
class PersonUserLinkerTest {

    private static final Instant NOW = Instant.parse("2026-03-01T09:00:00Z");
    private static final ClanId CLAN = ClanId.of(UUID.randomUUID());
    private static final ClanId OTHER_CLAN = ClanId.of(UUID.randomUUID());

    private StubNodes nodes;
    private StubMembers members;
    private PersonUserLinker linker;

    private PersonId an;
    private PersonId be;
    private UserId member;
    private UserId approver;

    @BeforeEach
    void setUp() {
        nodes = new StubNodes();
        members = new StubMembers();
        linker = new PersonUserLinker(nodes, members);

        an = nodes.add(CLAN, "Nguyễn Văn An");
        be = nodes.add(CLAN, "Nguyễn Thị Bé");
        member = UserId.of(UUID.randomUUID());
        approver = UserId.of(UUID.randomUUID());
        members.activate(CLAN, member);
    }

    @Test
    @DisplayName("linking an unlinked node to an active member succeeds")
    void happyPath() {
        LinkableNode linked = linker.link(CLAN, an, member, approver, NOW);

        assertThat(linked.linkedUserId()).isEqualTo(member);
        assertThat(linked.isLinkedTo(member)).isTrue();
    }

    @Test
    @DisplayName("linking the same account to the same node again is a no-op, not a conflict")
    void idempotent() {
        linker.link(CLAN, an, member, approver, NOW);

        assertThat(linker.link(CLAN, an, member, approver, NOW).linkedUserId()).isEqualTo(member);
    }

    @Test
    @DisplayName("I-K1: a node somebody else holds is 409 NODE_ALREADY_LINKED")
    void nodeAlreadyLinked() {
        UserId other = UserId.of(UUID.randomUUID());
        members.activate(CLAN, other);
        linker.link(CLAN, an, other, approver, NOW);

        assertThatThrownBy(() -> linker.link(CLAN, an, member, approver, NOW))
                .isInstanceOf(ConflictException.class)
                .extracting(failure -> ((ConflictException) failure).code())
                .isEqualTo(ErrorCode.NODE_ALREADY_LINKED);
    }

    @Test
    @DisplayName("I-P4: a second node in the same clan is 409 USER_ALREADY_LINKED_IN_CLAN")
    void userAlreadyLinkedInClan() {
        linker.link(CLAN, an, member, approver, NOW);

        assertThatThrownBy(() -> linker.link(CLAN, be, member, approver, NOW))
                .isInstanceOf(ConflictException.class)
                .extracting(failure -> ((ConflictException) failure).code())
                .isEqualTo(ErrorCode.USER_ALREADY_LINKED_IN_CLAN);
    }

    @Test
    @DisplayName("I-P4 is per clan: the same account may hold a node in another family")
    void oneNodePerClanNotPerAccount() {
        PersonId elsewhere = nodes.add(OTHER_CLAN, "Nguyễn Văn An");
        members.activate(OTHER_CLAN, member);
        linker.link(CLAN, an, member, approver, NOW);

        assertThat(linker.link(OTHER_CLAN, elsewhere, member, approver, NOW).linkedUserId())
                .isEqualTo(member);
    }

    @Test
    @DisplayName("I-P5: a non-member is 422, because the account and the clan are both real")
    void nonMemberIsRefused() {
        UserId stranger = UserId.of(UUID.randomUUID());

        assertThatThrownBy(() -> linker.link(CLAN, an, stranger, approver, NOW))
                .isInstanceOf(DomainRuleViolation.class)
                .extracting(failure -> ((DomainRuleViolation) failure).code())
                .isEqualTo(ErrorCode.VALIDATION_FAILED);
    }

    @Test
    @DisplayName("a node in another clan is a 404, never another family's row")
    void tenancy() {
        PersonId elsewhere = nodes.add(OTHER_CLAN, "Somebody else");

        assertThatThrownBy(() -> linker.link(CLAN, elsewhere, member, approver, NOW))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("losing the compare-and-set is 409, not a silent overwrite")
    void losesTheRace() {
        // What the real statement does when another transaction linked the row first: the read
        // above said unlinked, the write says otherwise.
        nodes.failNextWrite = true;

        assertThatThrownBy(() -> linker.link(CLAN, an, member, approver, NOW))
                .isInstanceOf(ConflictException.class)
                .extracting(failure -> ((ConflictException) failure).code())
                .isEqualTo(ErrorCode.NODE_ALREADY_LINKED);
    }

    @Test
    @DisplayName("unlinking returns the node to the pool")
    void unlink() {
        linker.link(CLAN, an, member, approver, NOW);

        assertThat(linker.unlink(CLAN, an, approver, NOW).isLinked()).isFalse();
        assertThat(linker.nodeOf(CLAN, member)).isEmpty();
    }

    @Test
    @DisplayName("unlinking a node nobody holds is not an error")
    void unlinkIdempotent() {
        assertThat(linker.unlink(CLAN, an, approver, NOW).isLinked()).isFalse();
    }

    @Test
    @DisplayName("the linker grants nothing — it has no way to (doc 04 §6)")
    void grantsNothing() {
        // Structural rather than behavioural, and deliberately so: there is no resolver and no
        // grant service on this class, so "approval quietly grants TREE_EDIT" is not a mistake it
        // is possible to make here. Phase 6 §8 names that as the likeliest error of the phase.
        assertThat(PersonUserLinker.class.getDeclaredFields())
                .extracting(field -> field.getType().getSimpleName())
                .containsExactlyInAnyOrder("PersonLinkPort", "ClanMemberPort");
    }

    /** A {@link PersonLinkPort} over a map, with a switch for the losing side of a race. */
    private static final class StubNodes implements PersonLinkPort {

        private final Map<PersonId, LinkableNode> rows = new LinkedHashMap<>();
        private boolean failNextWrite;

        PersonId add(ClanId clanId, String fullName) {
            PersonId id = PersonId.of(UUID.randomUUID());
            rows.put(id, new LinkableNode(id, clanId, BranchId.of(UUID.randomUUID()), fullName, null));
            return id;
        }

        @Override
        public Optional<LinkableNode> find(ClanId clanId, PersonId personId) {
            return Optional.ofNullable(rows.get(personId))
                    .filter(node -> node.clanId().equals(clanId));
        }

        @Override
        public Optional<LinkableNode> findLinkedTo(ClanId clanId, UserId userId) {
            return rows.values().stream()
                    .filter(node -> node.clanId().equals(clanId))
                    .filter(node -> node.isLinkedTo(userId))
                    .findFirst();
        }

        @Override
        public boolean linkIfUnlinked(
                ClanId clanId, PersonId personId, UserId userId, UserId actor, Instant now) {

            if (failNextWrite) {
                failNextWrite = false;
                return false;
            }
            LinkableNode node = rows.get(personId);
            if (node == null || node.isLinked()) {
                return false;
            }
            rows.put(personId, withUser(node, userId));
            return true;
        }

        @Override
        public boolean unlink(ClanId clanId, PersonId personId, UserId actor, Instant now) {
            LinkableNode node = rows.get(personId);
            if (node == null || !node.isLinked()) {
                return false;
            }
            rows.put(personId, withUser(node, null));
            return true;
        }

        private static LinkableNode withUser(LinkableNode node, UserId userId) {
            return new LinkableNode(
                    node.id(), node.clanId(), node.branchId(), node.fullName(), userId);
        }
    }

    /** A {@link ClanMemberPort} over a set of (clan, user) pairs. */
    private static final class StubMembers implements ClanMemberPort {

        private final Set<String> active = new HashSet<>();

        void activate(ClanId clanId, UserId userId) {
            active.add(clanId.value() + ":" + userId.value());
        }

        @Override
        public boolean isActiveMember(ClanId clanId, UserId userId) {
            return active.contains(clanId.value() + ":" + userId.value());
        }
    }
}
