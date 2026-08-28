package app.giaky.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import app.giaky.common.types.ClanId;
import app.giaky.common.types.TenantExempt;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.repository.Repository;
import org.springframework.web.bind.annotation.RestController;

/**
 * The six layering rules from {@code .docs/06-backend-architecture.md} §2.1.
 *
 * <p>These are the mechanism that keeps the architecture true. They are cheap now, while the
 * packages are still empty, and expensive to retrofit once a shortcut has been taken in fifty
 * places — which is exactly why they land in Phase 0 rather than when the first violation appears.
 *
 * <p>Most rules currently check an empty set of classes and pass trivially. That is expected:
 * {@code archunit.properties} turns off the empty-should failure so the suite is green from day
 * one, and each rule starts biting the moment its layer gains a class.
 */
class LayeringTest {

    /** The bounded contexts of {@code .docs/06-backend-architecture.md} §2. */
    private static final List<String> CONTEXTS =
            List.of(
                    "identity",
                    "clanorg",
                    "lineage",
                    "authorization",
                    "linking",
                    "media",
                    "notification");

    private static JavaClasses productionClasses;

    @BeforeAll
    static void importProductionClasses() {
        productionClasses =
                new ClassFileImporter()
                        .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                        .importPackages("app.giaky");
    }

    @Test
    @DisplayName("1. domain depends on nothing but the JDK and common.types")
    void domainIsFrameworkFree() {
        ArchRule rule =
                noClasses()
                        .that()
                        .resideInAPackage("..domain..")
                        .should()
                        .dependOnClassesThat()
                        .resideInAnyPackage(
                                "..infrastructure..",
                                "..api..",
                                "app.giaky.config..",
                                "app.giaky.common.web..",
                                "org.springframework..",
                                "jakarta.persistence..",
                                "jakarta.servlet..",
                                "com.fasterxml.jackson..",
                                "org.hibernate..")
                        .because(
                                "the domain holds the invariants of a permission-critical system and must be"
                                        + " unit-testable without Spring and without a database");

        rule.check(productionClasses);
    }

    @Test
    @DisplayName("2. application never depends on api")
    void applicationDoesNotDependOnApi() {
        ArchRule rule =
                noClasses()
                        .that()
                        .resideInAPackage("..application..")
                        .should()
                        .dependOnClassesThat()
                        .resideInAPackage("..api..")
                        .because(
                                "use cases are driven by the transport, not the other way round; an application"
                                        + " service that knows about a DTO cannot be reused by a job or a listener");

        rule.check(productionClasses);
    }

    @Test
    @DisplayName("3. api never touches repositories or JPA entities")
    void apiDoesNotDependOnInfrastructure() {
        ArchRule rule =
                noClasses()
                        .that()
                        .resideInAPackage("..api..")
                        .should()
                        .dependOnClassesThat()
                        .resideInAPackage("..infrastructure..")
                        .because(
                                "controllers bind, validate, delegate and map; reaching past the application"
                                        + " service is how the transaction boundary and the permission check get"
                                        + " skipped");

        rule.check(productionClasses);
    }

    /**
     * The one context whose <i>domain</i> is public API.
     *
     * <p>Doc 04 §1.4 requires every service in the product to route its decisions through one
     * resolver, and doc 06 §4.1 puts that resolver — with {@code Permission}, {@code Target} and
     * {@code PermissionScope}, the vocabulary its signature is written in — in
     * {@code authorization.domain}. A ClanOrg service calling {@code resolver.require(...)} therefore
     * depends on this package by design, and the B3.15 rule in {@link AuthorizationCoverageTest}
     * insists that it does.
     *
     * <p>The exemption is for the domain only. {@code authorization.infrastructure} — the grant table,
     * the adapters, the request cache — stays unreachable from every other context, so what is shared
     * is the vocabulary and the decision, never the storage.
     */
    private static final String AUTHORIZATION_KERNEL = "app.giaky.authorization.domain..";

    @Test
    @DisplayName("4. no context reaches into another context's domain or infrastructure")
    void contextsAreIsolated() {
        for (String context : CONTEXTS) {
            String domainPackage = "app.giaky." + context + ".domain..";
            String[] forbidden =
                    AUTHORIZATION_KERNEL.equals(domainPackage)
                            ? new String[] {"app.giaky." + context + ".infrastructure.."}
                            : new String[] {
                                domainPackage, "app.giaky." + context + ".infrastructure.."
                            };

            ArchRule rule =
                    noClasses()
                            .that()
                            .resideOutsideOfPackage("app.giaky." + context + "..")
                            .should()
                            .dependOnClassesThat()
                            .resideInAnyPackage(forbidden)
                            .because(
                                    "cross-context access goes through published application services or domain"
                                            + " events, so a context's internals stay free to change ("
                                            + context
                                            + ")");

            rule.check(productionClasses);
        }
    }

    @Test
    @DisplayName("5. controllers never inject repositories")
    void controllersDoNotInjectRepositories() {
        ArchRule rule =
                noClasses()
                        .that()
                        .areAnnotatedWith(RestController.class)
                        .should()
                        .dependOnClassesThat()
                        .areAssignableTo(Repository.class)
                        .because(
                                "a controller with a repository has no transaction and no permission check"
                                        + " between the request and the database");

        rule.check(productionClasses);
    }

    @Test
    @DisplayName("6. every repository read is clan-scoped or explicitly @TenantExempt")
    void repositoryReadsAreClanScoped() {
        ArchRule rule =
                classes()
                        .that()
                        .areAssignableTo(Repository.class)
                        .should(HAVE_ONLY_CLAN_SCOPED_READS)
                        .because(
                                "tenant isolation is only as good as its weakest query; a read without a ClanId"
                                        + " is one id substitution away from serving another clan's data");

        rule.check(productionClasses);
    }

    // --- rule 6's condition ------------------------------------------------------------------

    /** Method-name prefixes Spring Data derives queries from, plus our own conventions. */
    private static final Pattern READ_METHOD =
            Pattern.compile("^(find|get|read|query|search|count|exists|stream)([A-Z].*)?$");

    /**
     * Methods every {@code Repository} inherits or that Spring Data implements for us. We cannot
     * change their signatures, and a blanket exemption annotation on each would be noise — the real
     * defence is that the derived, hand-written finders below them must be scoped.
     */
    private static final Set<String> INHERITED_METHODS =
            Set.of("findById", "findAll", "findAllById", "count", "existsById");

    private static final ArchCondition<JavaClass> HAVE_ONLY_CLAN_SCOPED_READS =
            new ArchCondition<>("only declare reads that take a ClanId or are @TenantExempt") {

                @Override
                public void check(JavaClass repository, ConditionEvents events) {
                    if (repository.isAnnotatedWith(TenantExempt.class)) {
                        return;
                    }
                    for (JavaMethod method : repository.getMethods()) {
                        if (isUnscopedRead(method)) {
                            events.add(
                                    SimpleConditionEvent.violated(
                                            method,
                                            method.getFullName()
                                                    + " reads without a ClanId and carries no @TenantExempt"
                                                    + " justification"));
                        }
                    }
                }
            };

    private static boolean isUnscopedRead(JavaMethod method) {
        String name = method.getName();
        if (!READ_METHOD.matcher(name).matches() || INHERITED_METHODS.contains(name)) {
            return false;
        }
        if (method.isAnnotatedWith(TenantExempt.class)) {
            return false;
        }
        return method.getRawParameterTypes().stream()
                .noneMatch(type -> ClanId.class.getName().equals(type.getName()));
    }
}
