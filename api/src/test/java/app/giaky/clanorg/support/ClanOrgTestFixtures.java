package app.giaky.clanorg.support;

import app.giaky.clanorg.application.BranchService;
import app.giaky.clanorg.application.ClanService;
import app.giaky.clanorg.application.CreateBranchCommand;
import app.giaky.clanorg.application.CreateClanCommand;
import app.giaky.clanorg.application.InterimClanAccessCheck;
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

/**
 * The whole ClanOrg application graph, wired in memory.
 *
 * <p>Every service here is the real one. Only the four repositories, the audit writer, the event
 * publisher and the clock are doubles — which is the point: these tests exercise the code that will
 * run in production, and the parts they replace are the parts that would otherwise need a container.
 *
 * <p>The clock is mutable so a test can age a join request past its ninety days without waiting for
 * them; ids are sequential so a failure names a branch a reader can find.
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
    public final InMemoryUserRepository users = new InMemoryUserRepository();
    public final RecordingAuditWriter audit = new RecordingAuditWriter();
    public final RecordingEventPublisher events = new RecordingEventPublisher();

    public final IdGenerator ids = new SequentialIdGenerator();
    public final MovableClock clock = new MovableClock(NOW);

    public final InterimClanAccessCheck access = new InterimClanAccessCheck(clans, memberships);
    public final BranchPathService paths = new BranchPathService(branches);
    /**
     * Seeded rather than secure, so a failing test produces the same codes on the next run. The
     * production wiring uses a {@code SecureRandom}; see {@code CodeGenerator}.
     */
    public final CodeGenerator codes = new CodeGenerator(new Random(20260101L));

    public final UserDirectory directory = new UserDirectory(users);

    public final ClanService clanService =
            new ClanService(
                    clans, branches, memberships, joinRequests, codes, access, audit, events, ids, clock);

    public final BranchService branchService =
            new BranchService(branches, memberships, paths, codes, access, audit, events, ids, clock);

    public final MembershipService membershipService =
            new MembershipService(
                    memberships, branches, access, access, directory, audit, events, clock);

    public final JoinRequestService joinRequestService =
            new JoinRequestService(
                    joinRequests,
                    clans,
                    branches,
                    memberships,
                    access,
                    directory,
                    audit,
                    events,
                    ids,
                    clock);

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

    /** A clan with its root branch and its founder's membership, through the real service. */
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
     * An {@code ApplicationEventPublisher} that keeps what it was given.
     *
     * <p>The events are a published contract that Phase 3 subscribes to (doc 02 §4), so the tests
     * assert they were raised rather than trusting that a later phase will notice.
     */
    public static final class RecordingEventPublisher
            implements org.springframework.context.ApplicationEventPublisher {

        private final List<Object> published = new ArrayList<>();

        @Override
        public void publishEvent(Object event) {
            published.add(event);
        }

        public List<Object> published() {
            return List.copyOf(published);
        }

        /** Every published event of one type. */
        public <T> List<T> ofType(Class<T> type) {
            return published.stream().filter(type::isInstance).map(type::cast).toList();
        }
    }
}
