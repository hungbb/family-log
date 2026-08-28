package app.giaky.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.giaky.authorization.domain.PermissionResolver;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

/**
 * <b>B3.15 — the structural guarantee against a forgotten permission check.</b>
 *
 * <p>Every other defence in Phase 3 protects against a check that is <i>wrong</i>. This one protects
 * against a check that is <i>absent</i>, which is the failure that actually happens: somebody adds a
 * seventh method to a service six months from now, copies the shape of its neighbours, and misses
 * the one line that matters. Review catches that most of the time. Most of the time is not the bar
 * for the component the product's central promise rests on (phase 3 §8, roadmap risk R1).
 *
 * <p><b>The rule.</b> A public method on an application service that is {@code @Transactional} and
 * not {@code readOnly} is, by the conventions of doc 09 §2.2, a mutation. It must call {@code
 * PermissionResolver.require(...)}, or appear in {@link #ALLOW_LIST} with a reason a person wrote.
 *
 * <p>Read methods are excluded, not overlooked. Reads are clan-wide in v1 (doc 04 §5.5) and are
 * gated by {@code requireMembership}; when read partitioning arrives, this rule grows a second half
 * rather than being loosened.
 */
class AuthorizationCoverageTest {

    /**
     * Mutating methods that legitimately ask nobody's permission, and why.
     *
     * <p>Every entry is a decision. Adding one should feel like an edit to the security model,
     * because it is — which is why the value is a justification rather than a boolean, and why the
     * list is short enough to read in full.
     */
    private static final Map<String, String> ALLOW_LIST = allowList();

    private static Map<String, String> allowList() {
        Map<String, String> allowed = new LinkedHashMap<>();

        allowed.put(
                "app.giaky.clanorg.application.ClanService.create",
                "Founding a clan is open to any authenticated account (FR-C1). There is no clan to"
                    + " hold a permission in yet — this is the call that creates the first one, and"
                    + " the ClanCreated listener issues the founder's CLAN_ADMIN inside the same"
                    + " transaction.");
        allowed.put(
                "app.giaky.clanorg.application.JoinRequestService.submit",
                "Asking to join is what somebody does before they are a member (FR-J1). Requiring a"
                    + " permission here would make the clan unjoinable.");
        allowed.put(
                "app.giaky.clanorg.application.JoinRequestService.cancel",
                "Withdrawing one's own request (FR-J5). The repository lookup is scoped to the"
                    + " requester's own id, so the row cannot belong to anyone else.");
        allowed.put(
                "app.giaky.clanorg.application.JoinRequestService.expireOverdue",
                "A scheduled sweep with no human actor. It closes requests the calendar has already"
                    + " expired and grants nothing.");
        allowed.put(
                "app.giaky.clanorg.application.MembershipService.leave",
                "Leaving is a member's own business. Guarded by requireMembership and by"
                    + " LastAdminProtection, which is the rule that actually applies here (I-M3).");
        allowed.put(
                "app.giaky.authorization.application.GrantService.markExpiredRevoked",
                "The grant expiry sweep (B3.14). Hygiene, not enforcement: every read path already"
                    + " filters on expires_at, so this changes tidiness rather than access.");

        return Map.copyOf(allowed);
    }

    /**
     * Contexts with no clan to hold a permission in.
     *
     * <p>Identity's services act on the caller's own account, before any clan is known — a
     * registration, a login, a password reset. Asking them for a clan-scoped permission would be
     * asking a question with no subject.
     */
    private static final Set<String> EXEMPT_PACKAGES =
            Set.of("app.giaky.identity.application");

    private static JavaClasses productionClasses;

    @BeforeAll
    static void importProductionClasses() {
        productionClasses =
                new ClassFileImporter()
                        .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                        .importPackages("app.giaky");
    }

    @Test
    @DisplayName("every mutating application-service method calls require(...) or is allow-listed")
    void everyMutationIsGuarded() {
        rule().check(productionClasses);
    }

    @Test
    @DisplayName("the rule fails on a service that forgot its check")
    void ruleCatchesAnUnguardedService() {
        // Phase 3 §5: "verified with a deliberately broken fixture". A rule only ever run against
        // compliant code is a rule nobody has tested.
        JavaClasses brokenFixture =
                new ClassFileImporter().importPackages("app.giaky.architecture.fixtures.application");

        assertThatThrownBy(() -> rule().check(brokenFixture))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("UnguardedClanService")
                .hasMessageContaining("renameClan");
    }

    @Test
    @DisplayName("the allow-list names methods that exist")
    void allowListDoesNotRot() {
        // An entry for a method that has been renamed or deleted is an exemption nobody is applying
        // and nobody will notice has stopped applying — until it is copied onto something else.
        Set<String> existing = new HashSet<>();
        productionClasses.stream()
                .filter(type -> type.getPackageName().endsWith(".application"))
                .forEach(
                        type ->
                                type.getMethods()
                                        .forEach(
                                                method ->
                                                        existing.add(
                                                                type.getName()
                                                                        + "."
                                                                        + method.getName())));

        assertThat(existing).containsAll(ALLOW_LIST.keySet());
    }

    private static ArchRule rule() {
        return methods()
                .that()
                .areDeclaredInClassesThat()
                .resideInAPackage("..application..")
                .and()
                .arePublic()
                .and(com.tngtech.archunit.base.DescribedPredicate.describe(
                        "are mutating transactions", AuthorizationCoverageTest::isMutatingTransaction))
                .should(CALL_REQUIRE)
                .because(
                        "a mutation that never asks the resolver is a mutation anybody can perform;"
                            + " doc 04 §5.1 makes the service-level check the contract, and this is"
                            + " what stops the next one being forgotten");
    }

    private static boolean isMutatingTransaction(JavaMethod method) {
        if (EXEMPT_PACKAGES.contains(method.getOwner().getPackageName())) {
            return false;
        }
        if (!method.isAnnotatedWith(Transactional.class)) {
            return false;
        }
        // readOnly = true is the convention's word for "this is a read" (doc 09 §2.2). Reads are
        // clan-wide in v1 and gated by requireMembership instead (doc 04 §5.5).
        return !method.getAnnotationOfType(Transactional.class).readOnly();
    }

    private static final ArchCondition<JavaMethod> CALL_REQUIRE =
            new ArchCondition<>("call PermissionResolver.require(...) or be allow-listed") {

                @Override
                public void check(JavaMethod method, ConditionEvents events) {
                    String name = method.getOwner().getName() + "." + method.getName();
                    if (ALLOW_LIST.containsKey(name)) {
                        return;
                    }
                    if (callsRequire(method)) {
                        return;
                    }
                    events.add(
                            SimpleConditionEvent.violated(
                                    method,
                                    method.getFullName()
                                            + " mutates without calling"
                                            + " PermissionResolver.require(...), and is not on the"
                                            + " allow-list in AuthorizationCoverageTest"));
                }
            };

    /**
     * Whether the method reaches the resolver, directly or through one of its own class's helpers.
     *
     * <p>One level of indirection is followed on purpose. {@code GrantService.grant} wraps its call
     * in a {@code try} so that a refusal becomes {@code PERMISSION_ESCALATION_BLOCKED}, and a
     * service that extracts a two-line guard into a private method has not stopped checking. Two
     * levels would start to be a call-graph analysis with a different failure mode; one is enough
     * to be useful and shallow enough to reason about.
     */
    private static boolean callsRequire(JavaMethod method) {
        if (callsRequireDirectly(method)) {
            return true;
        }
        return method.getMethodCallsFromSelf().stream()
                .filter(call -> call.getTargetOwner().equals(method.getOwner()))
                .map(JavaMethodCall::getTarget)
                .flatMap(target -> target.resolveMember().stream())
                .anyMatch(AuthorizationCoverageTest::callsRequireDirectly);
    }

    private static boolean callsRequireDirectly(JavaMethod method) {
        return method.getCallsFromSelf().stream()
                .anyMatch(
                        call ->
                                call.getTargetOwner().isAssignableTo(PermissionResolver.class)
                                        && "require".equals(call.getName()));
    }
}
