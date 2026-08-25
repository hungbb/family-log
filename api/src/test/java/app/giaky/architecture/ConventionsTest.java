package app.giaky.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import jakarta.persistence.Entity;
import java.util.Optional;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RestController;

/**
 * The code rules from {@code .docs/09-engineering-conventions.md} §2.2 that a machine can check.
 *
 * <p>Separate from {@link LayeringTest} on purpose: those six rules are the architecture, these are
 * house style. Breaking one of these is a mistake; breaking one of those is a design change.
 */
class ConventionsTest {

    private static JavaClasses productionClasses;

    @BeforeAll
    static void importProductionClasses() {
        productionClasses =
                new ClassFileImporter()
                        .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                        .importPackages("app.giaky");
    }

    @Test
    @DisplayName("constructor injection only — no @Autowired fields")
    void noFieldInjection() {
        noFields()
                .should()
                .beAnnotatedWith(Autowired.class)
                .because(
                        "field injection hides a class's dependencies from its constructor, which is also"
                                + " what makes it untestable without Spring")
                .check(productionClasses);
    }

    @Test
    @DisplayName("@Transactional only on application services")
    void transactionsBelongToTheApplicationLayer() {
        noMethods()
                .that()
                .areDeclaredInClassesThat()
                .resideOutsideOfPackage("..application..")
                .should()
                .beAnnotatedWith(Transactional.class)
                .because(
                        "the transaction boundary is the use case; a transactional controller holds the"
                                + " connection open across serialization, and a transactional repository"
                                + " silently splits one use case into several")
                .check(productionClasses);
    }

    @Test
    @DisplayName("controllers are not transactional")
    void controllersAreNotTransactional() {
        noClasses()
                .that()
                .areAnnotatedWith(RestController.class)
                .should()
                .beAnnotatedWith(Transactional.class)
                .check(productionClasses);
    }

    @Test
    @DisplayName("nothing writes to System.out or System.err")
    void noConsolePrinting() {
        noClasses()
                .should()
                .accessField(System.class, "out")
                .orShould()
                .accessField(System.class, "err")
                .because("logs are structured and carry the correlation id; println has neither")
                .check(productionClasses);
    }

    @Test
    @DisplayName("Optional is a return type, never a parameter")
    void optionalIsNotAParameter() {
        noMethods()
                .should()
                .haveRawParameterTypes(
                        com.tngtech.archunit.base.DescribedPredicate.describe(
                                "an Optional",
                                types ->
                                        types.stream()
                                                .anyMatch(
                                                        type ->
                                                                Optional.class
                                                                        .getName()
                                                                        .equals(type.getName()))))
                .because(
                        "an Optional parameter has three states — present, empty, null — where the caller"
                                + " meant two; overload, or pass the value")
                .check(productionClasses);
    }

    @Test
    @DisplayName("controllers are named *Controller")
    void controllersAreNamedController() {
        classes()
                .that()
                .areAnnotatedWith(RestController.class)
                .should()
                .haveSimpleNameEndingWith("Controller")
                .check(productionClasses);
    }

    @Test
    @DisplayName("JPA entities are named *JpaEntity and live in infrastructure")
    void jpaEntitiesAreNamedAndPlaced() {
        classes()
                .that()
                .areAnnotatedWith(Entity.class)
                .should()
                .haveSimpleNameEndingWith("JpaEntity")
                .andShould()
                .resideInAPackage("..infrastructure..")
                .because(
                        "the naming keeps a persistence class from being mistaken for a domain object in a"
                                + " review, and the placement keeps the domain persistence-ignorant")
                .check(productionClasses);
    }
}
