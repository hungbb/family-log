package app.giaky.authorization.support;

import app.giaky.authorization.domain.BranchHierarchyPort;
import app.giaky.authorization.domain.GrantPort;
import app.giaky.authorization.domain.LineageAncestryPort;
import app.giaky.authorization.domain.MembershipPort;
import app.giaky.authorization.domain.Permission;
import app.giaky.authorization.domain.PermissionGrant;
import app.giaky.authorization.domain.PermissionResolver;
import app.giaky.authorization.domain.PermissionScope;
import app.giaky.common.types.BranchId;
import app.giaky.common.types.ClanId;
import app.giaky.common.types.UserId;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A {@link PermissionResolver} with four hand-written ports and no Spring, no database and no clock.
 *
 * <p>This is the shape doc 04 §8 asks for, and it is the whole reason the resolver is a plain class
 * over four interfaces: the rules that decide who may change a family's records are testable by
 * stating a world in six lines and asking one question. Everything the fixture holds is a fact the
 * production adapters would have read from somewhere — a membership row, a grant row, a branch path,
 * the ancestry closure — and nothing is a stand-in for logic.
 *
 * <p>Branch paths are built the way {@code MaterializedPath} builds them, with the trailing slash,
 * because prefix comparison is the rule under test and a fixture that used a different convention
 * would be testing itself.
 */
public final class ResolverFixture {

    /** The instant the {@code test} profile freezes at, so expiry arithmetic reads the same way. */
    public static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    private static final AtomicLong SEQUENCE = new AtomicLong();

    public final ClanId clanId = newClanId();
    /** A second clan, for the tenancy checks. Nothing is ever set up in it. */
    public final ClanId otherClanId = newClanId();

    private final Set<UserId> members = new HashSet<>();
    private final Map<UserId, List<PermissionGrant>> grantsByUser = new HashMap<>();
    private final Map<BranchId, String> pathsByBranch = new LinkedHashMap<>();
    private final Map<BranchId, UUID> rootPersonByBranch = new HashMap<>();

    private boolean ancestryAvailable;
    private final Map<UUID, Set<UUID>> descendantsByAncestor = new HashMap<>();
    private final Map<UUID, BranchId> placementByPerson = new HashMap<>();

    /** How many times the grant port has been asked. Proves the request cache is doing its job. */
    public int grantLookups;

    public final PermissionResolver resolver =
            new PermissionResolver(
                    new MembershipPort() {
                        @Override
                        public boolean isActiveMember(ClanId clan, UserId user) {
                            return clan.equals(clanId) && members.contains(user);
                        }
                    },
                    new GrantPort() {
                        @Override
                        public List<PermissionGrant> effectiveGrants(ClanId clan, UserId user) {
                            grantLookups++;
                            return List.copyOf(
                                    grantsByUser.getOrDefault(user, List.of()));
                        }

                        @Override
                        public void invalidate(ClanId clan, UserId user) {
                            // Nothing is cached here.
                        }
                    },
                    new BranchHierarchyPort() {
                        @Override
                        public Optional<String> pathOf(ClanId clan, BranchId branch) {
                            return clan.equals(clanId)
                                    ? Optional.ofNullable(pathsByBranch.get(branch))
                                    : Optional.empty();
                        }

                        @Override
                        public List<BranchPath> allPaths(ClanId clan) {
                            List<BranchPath> paths = new ArrayList<>();
                            if (clan.equals(clanId)) {
                                pathsByBranch.forEach(
                                        (branch, path) -> paths.add(new BranchPath(branch, path)));
                            }
                            return paths;
                        }

                        @Override
                        public Optional<UUID> rootPersonOf(ClanId clan, BranchId branch) {
                            return Optional.ofNullable(rootPersonByBranch.get(branch));
                        }
                    },
                    new LineageAncestryPort() {
                        @Override
                        public boolean isAvailable() {
                            return ancestryAvailable;
                        }

                        @Override
                        public boolean covers(ClanId clan, UUID ancestor, UUID candidate) {
                            requireAvailable();
                            return descendantsOf(clan, ancestor).contains(candidate);
                        }

                        @Override
                        public Set<UUID> descendantsOf(ClanId clan, UUID ancestor) {
                            requireAvailable();
                            Set<UUID> descendants =
                                    new HashSet<>(
                                            descendantsByAncestor.getOrDefault(ancestor, Set.of()));
                            // The self-row at depth 0 (doc 03 §3.10): a subtree grant covers the
                            // person it names.
                            descendants.add(ancestor);
                            return descendants;
                        }

                        @Override
                        public Optional<BranchId> placementOf(ClanId clan, UUID personId) {
                            requireAvailable();
                            return Optional.ofNullable(placementByPerson.get(personId));
                        }

                        private void requireAvailable() {
                            if (!ancestryAvailable) {
                                // Exactly what DeferredLineageAncestryAdapter does, so a caller
                                // that forgets to check isAvailable() fails here too.
                                throw new IllegalStateException(
                                        "person_ancestry does not exist until Phase 4");
                            }
                        }
                    },
                    Clock.fixed(NOW, ZoneOffset.UTC));

    // --- building a world ----------------------------------------------------------------------

    public UserId member() {
        UserId user = newUserId();
        members.add(user);
        return user;
    }

    /** Somebody with an account but no membership here — every decision about them is a 404. */
    public UserId outsider() {
        return newUserId();
    }

    /** Ends somebody's membership without touching their grants (doc 04 §1.6). */
    public void endMembership(UserId user) {
        members.remove(user);
    }

    /** A root branch: {@code /aaaaaaaa/}. */
    public BranchId rootBranch() {
        BranchId branch = newBranchId();
        pathsByBranch.put(branch, "/" + branch.pathSegment() + "/");
        return branch;
    }

    /** A branch under {@code parent}, with the path that placement implies. */
    public BranchId branchUnder(BranchId parent) {
        BranchId branch = newBranchId();
        pathsByBranch.put(branch, pathsByBranch.get(parent) + branch.pathSegment() + "/");
        return branch;
    }

    /** A branch id this clan does not have — the "cannot classify" case (doc 04 §1.5). */
    public BranchId unknownBranch() {
        return newBranchId();
    }

    public void branchRootPerson(BranchId branch, UUID personId) {
        rootPersonByBranch.put(branch, personId);
    }

    /** Turns on {@code person_ancestry}, as Phase 4 will. */
    public void enableAncestry() {
        ancestryAvailable = true;
    }

    public void descendants(UUID ancestor, UUID... descendants) {
        descendantsByAncestor.computeIfAbsent(ancestor, key -> new HashSet<>()).addAll(Set.of(descendants));
    }

    public void placePerson(UUID personId, BranchId branch) {
        placementByPerson.put(personId, branch);
    }

    /** A live grant, effective from the beginning of time until somebody takes it away. */
    public PermissionGrant grant(UserId user, Permission permission, PermissionScope scope) {
        return record(
                PermissionGrant.issue(
                        newId(), clanId, user, permission, scope, user, null, Instant.EPOCH));
    }

    /** A grant that lapses at {@code expiresAt}. */
    public PermissionGrant grantUntil(
            UserId user, Permission permission, PermissionScope scope, Instant expiresAt) {

        return record(
                PermissionGrant.issue(
                        newId(), clanId, user, permission, scope, user, expiresAt, Instant.EPOCH));
    }

    /** A grant somebody has already taken back. It must confer nothing. */
    public PermissionGrant revokedGrant(
            UserId user, Permission permission, PermissionScope scope) {

        return record(
                PermissionGrant.issue(
                                newId(),
                                clanId,
                                user,
                                permission,
                                scope,
                                user,
                                null,
                                Instant.EPOCH)
                        .revoke(user, Instant.EPOCH.plusSeconds(1)));
    }

    /**
     * Puts a grant in front of the resolver whether or not it is effective.
     *
     * <p>The production {@code GrantPort} filters revoked and expired rows out at the source, so a
     * resolver that trusted the port would pass these tests too. Handing them over unfiltered is
     * what proves the resolver is not the thing doing the filtering — and that a bug in the filter
     * would be caught rather than compensated for.
     */
    private PermissionGrant record(PermissionGrant grant) {
        grantsByUser.computeIfAbsent(grant.granteeUserId(), key -> new ArrayList<>()).add(grant);
        return grant;
    }

    // --- ids -----------------------------------------------------------------------------------

    /**
     * Sequential rather than random, so a failing assertion names an id a reader can find in the
     * fixture above it. Distinct high bits keep clan, user and branch ids from ever colliding.
     */
    public static UUID newId() {
        // The counter goes in the high bits so that BranchId.pathSegment() — the first eight hex
        // characters — differs between branches. With it in the low bits every branch would share
        // the segment "00000000" and every path prefix comparison would be trivially true.
        return new UUID(SEQUENCE.incrementAndGet() << 32, 0x1000L);
    }

    public static ClanId newClanId() {
        return ClanId.of(newId());
    }

    public static UserId newUserId() {
        return UserId.of(newId());
    }

    public static BranchId newBranchId() {
        return BranchId.of(newId());
    }
}
