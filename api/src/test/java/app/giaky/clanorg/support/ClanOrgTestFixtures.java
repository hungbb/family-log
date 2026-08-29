package app.giaky.clanorg.support;

import app.giaky.authorization.application.ExpiredGrantSweepJob;
import app.giaky.authorization.application.GrantBackedLastAdminProtection;
import app.giaky.authorization.application.GrantDecorator;
import app.giaky.authorization.application.GrantLifecycleListener;
import app.giaky.authorization.application.GrantService;
import app.giaky.authorization.application.PermissionQueryService;
import app.giaky.authorization.domain.PermissionResolver;
import app.giaky.authorization.infrastructure.AuthorizationRequestCache;
import app.giaky.authorization.infrastructure.CachingGrantAdapter;
import app.giaky.authorization.infrastructure.ClanOrgBranchHierarchyAdapter;
import app.giaky.authorization.infrastructure.ClanOrgMembershipAdapter;
import app.giaky.authorization.support.InMemoryLineageAncestryPort;
import app.giaky.authorization.support.InMemoryPermissionGrantRepository;
import app.giaky.clanorg.application.BranchService;
import app.giaky.clanorg.application.ClanDirectory;
import app.giaky.clanorg.application.ClanService;
import app.giaky.clanorg.application.CreateBranchCommand;
import app.giaky.clanorg.application.CreateClanCommand;
import app.giaky.clanorg.application.JoinRequestService;
import app.giaky.clanorg.application.MembershipService;
import app.giaky.clanorg.domain.BranchPathService;
import app.giaky.clanorg.domain.CodeGenerator;
import app.giaky.common.types.BranchId;
import app.giaky.common.types.ClanId;
import app.giaky.common.types.IdGenerator;
import app.giaky.common.types.SequentialIdGenerator;
import app.giaky.common.types.UserId;
import app.giaky.identity.application.UserDirectory;
import app.giaky.identity.domain.EmailAddress;
import app.giaky.identity.domain.LocaleTag;
import app.giaky.identity.domain.PasswordHash;
import app.giaky.identity.domain.User;
import app.giaky.identity.support.InMemoryUserRepository;
import app.giaky.identity.support.RecordingAuditWriter;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Consumer;

/**
 * The whole ClanOrg and Authorization application graph, wired in memory.
 *
 * <p>Every service here is the real one, and from Phase 3 that includes the permission resolver and
 * its three adapters. Only the repositories, the audit writer, the event publisher and the clock are
 * doubles — which is the point: these tests exercise the code that will run in production, and the
 * parts they replace are the parts that would otherwise need a container.
 *
 * <p><b>Events are dispatched.</b> Phase 2's publisher only recorded; Phase 3's grant lifecycle
 * lives in listeners, so a recorder alone would mean the founder never got their {@code CLAN_ADMIN}
 * and half these tests would be describing a world the application does not produce. It still
 * records — assertions on published events are unchanged — and now also delivers.
 *
 * <p>The clock is mutable so a test can age a join request past its ninety days, or a grant past its
 * expiry, without waiting for them; ids are sequential so a failure names a branch a reader can find.
 */
public final class ClanOrgTestFixtures {

    /** The same instant {@code CoreConfig} freezes the {@code test} profile at. */
    public static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    public final InMemoryClanRepository clans = new InMemoryClanRepository();
    public final InMemoryBranchRepository branches = new InMemoryBranchRepository();
    public final InMemoryClanMembershipRepository memberships =
            new InMemoryClanMembershipRepository();
    public final InMemoryClanJoinRequestRepository joinRequests =
            new InMemoryClanJoinRequestRepository();
    public final InMemoryPermissionGrantRepository grants =
            new InMemoryPermissionGrantRepository();
    public final InMemoryUserRepository users = new InMemoryUserRepository();
    public final RecordingAuditWriter audit = new RecordingAuditWriter();
    public final RecordingEventPublisher events = new RecordingEventPublisher();

    public final IdGenerator ids = new SequentialIdGenerator();
    public final MovableClock clock = new MovableClock(NOW);

    public final BranchPathService paths = new BranchPathService(branches);
    /**
     * Seeded rather than secure, so a failing test produces the same codes on the next run. The
     * production wiring uses a {@code SecureRandom}; see {@code CodeGenerator}.
     */
    public final CodeGenerator codes = new CodeGenerator(new Random(20260101L));

    public final UserDirectory directory = new UserDirectory(users);
    public final ClanDirectory clanDirectory = new ClanDirectory(memberships, branches, clans);

    // The real adapters. Outside a request the cache simply is not there and every lookup goes to
    // the repository, which is exactly what AuthorizationRequestCache promises.
    public final AuthorizationRequestCache requestCache = new AuthorizationRequestCache();
    public final CachingGrantAdapter grantPort = new CachingGrantAdapter(grants, requestCache, clock);

    /**
     * The lineage closure, in a map.
     *
     * <p>Phase 3 wired the deferred adapter here, which answered "no lineage yet". Phase 4 turns
     * {@code SUBTREE} scope on, so these fixtures need something that answers — tests that care
     * about subtree scope add people and edges to it, and the rest never touch it.
     */
    public final InMemoryLineageAncestryPort ancestry = new InMemoryLineageAncestryPort();

    public final PermissionResolver resolver =
            new PermissionResolver(
                    new ClanOrgMembershipAdapter(clanDirectory),
                    grantPort,
                    new ClanOrgBranchHierarchyAdapter(clanDirectory, requestCache),
                    ancestry,
                    clock);

    public final GrantDecorator grantDecorator =
            new GrantDecorator(clanDirectory, directory, ancestry);

    public final GrantService grantService =
            new GrantService(
                    grants,
                    resolver,
                    grantPort,
                    clanDirectory,
                    ancestry,
                    grantDecorator,
                    audit,
                    ids,
                    clock);

    public final PermissionQueryService permissions =
            new PermissionQueryService(resolver, grants, grantDecorator, clock);

    public final GrantBackedLastAdminProtection lastAdmin =
            new GrantBackedLastAdminProtection(grantService);

    public final GrantLifecycleListener grantLifecycle = new GrantLifecycleListener(grantService);

    public final ClanService clanService =
            new ClanService(
                    clans,
                    branches,
                    memberships,
                    joinRequests,
                    codes,
                    resolver,
                    permissions,
                    audit,
                    events,
                    ids,
                    clock);

    public final BranchService branchService =
            new BranchService(
                    branches,
                    memberships,
                    clans,
                    paths,
                    codes,
                    resolver,
                    audit,
                    events,
                    ids,
                    clock);

    public final MembershipService membershipService =
            new MembershipService(
                    memberships,
                    branches,
                    resolver,
                    permissions,
                    lastAdmin,
                    directory,
                    audit,
                    events,
                    clock);

    public final JoinRequestService joinRequestService =
            new JoinRequestService(
                    joinRequests,
                    clans,
                    branches,
                    memberships,
                    resolver,
                    directory,
                    audit,
                    events,
                    ids,
                    clock);

    public ClanOrgTestFixtures() {
        // Spring does this by annotation; here it is one line, and stating it makes the coupling
        // between "a clan was created" and "somebody can administer it" visible in the fixture.
        events.subscribe(
                app.giaky.clanorg.application.event.ClanCreated.class,
                grantLifecycle::onClanCreated);
        events.subscribe(
                app.giaky.clanorg.application.event.MembershipGranted.class,
                grantLifecycle::onMembershipGranted);
        events.subscribe(
                app.giaky.clanorg.application.event.MembershipRevoked.class,
                grantLifecycle::onMembershipRevoked);
    }

    private int accountCounter;

    /** An account that exists, so a member list has a name to show. */
    public UserId newUser(String displayName) {
        accountCounter++;
        UserId id = UserId.of(ids.newId());
        users.save(
                User.register(
                        id,
                        EmailAddress.of("member-" + accountCounter + "@example.com"),
                        PasswordHash.of("$2a$12$notarealhashnotarealhashnotarealhashnotarealhash00"),
                        displayName,
                        LocaleTag.DEFAULT,
                        NOW));
        return id;
    }

    /** A clan with its root branch, its founder's membership, and the founder's {@code CLAN_ADMIN}. */
    public ClanId newClan(UserId founder, String name) {
        return ClanId.of(
                clanService
                        .create(founder, new CreateClanCommand(name, null, "Nam Định", null))
                        .id());
    }

    /** A branch under {@code parent}, or under the root when {@code parent} is null. */
    public BranchId newBranch(UserId founder, ClanId clanId, BranchId parent, String name) {
        return BranchId.of(
                branchService
                        .create(founder, clanId, new CreateBranchCommand(parent, name, null))
                        .id());
    }

    /** The job wrapper, for the one test that checks it delegates. */
    public ExpiredGrantSweepJob expiredGrantSweepJob(
            app.giaky.common.scheduling.SingleRunGuard guard) {
        return new ExpiredGrantSweepJob(grantService, guard);
    }

    /** Moves the clock forward, for expiry. */
    public void advance(Duration amount) {
        clock.advance(amount);
    }

    /** A {@link Clock} a test can move. */
    public static final class MovableClock extends Clock {

        private Instant now;

        public MovableClock(Instant start) {
            this.now = start;
        }

        public void advance(Duration amount) {
            now = now.plus(amount);
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    /**
     * An {@code ApplicationEventPublisher} that keeps what it was given <i>and</i> delivers it.
     *
     * <p>The events are a published contract (doc 02 §4), so the tests assert they were raised. From
     * Phase 3 they are also load-bearing: {@code GrantLifecycleListener} turns clan creation into
     * the founder's grant, inside the same transaction. A publisher that only recorded would leave
     * every fixture clan with no administrator, which is a state the application cannot produce.
     */
    public static final class RecordingEventPublisher
            implements org.springframework.context.ApplicationEventPublisher {

        private final List<Object> published = new ArrayList<>();
        private final List<Subscription<?>> subscriptions = new ArrayList<>();

        @Override
        public void publishEvent(Object event) {
            published.add(event);
            for (Subscription<?> subscription : List.copyOf(subscriptions)) {
                subscription.deliver(event);
            }
        }

        public <T> void subscribe(Class<T> type, Consumer<T> listener) {
            subscriptions.add(new Subscription<>(type, listener));
        }

        public List<Object> published() {
            return List.copyOf(published);
        }

        /** Every published event of one type. */
        public <T> List<T> ofType(Class<T> type) {
            return published.stream().filter(type::isInstance).map(type::cast).toList();
        }

        private record Subscription<T>(Class<T> type, Consumer<T> listener) {

            void deliver(Object event) {
                if (type.isInstance(event)) {
                    listener.accept(type.cast(event));
                }
            }
        }
    }
}
