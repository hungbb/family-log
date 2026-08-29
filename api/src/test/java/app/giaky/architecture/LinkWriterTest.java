package app.giaky.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import app.giaky.lineage.domain.PersonRepository;
import app.giaky.linking.domain.PersonLinkPort;
import app.giaky.linking.domain.PersonUserLinker;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * <b>Phase 6 exit criterion 2 — {@code person.linked_user_id} is written by exactly one class.</b>
 *
 * <p>The criterion says "grep-verified", and a grep is a thing somebody has to remember to run. This
 * is the same check with the build behind it.
 *
 * <p>Three invariants are worth the ceremony, and they are three because the write crosses three
 * layers by design (Linking may not import {@code lineage.domain}):
 *
 * <ol>
 *   <li>Only {@link PersonUserLinker} calls the mutating methods of {@link PersonLinkPort}. That is
 *       what makes I-P4, I-P5 and I-K1 unavoidable — a service that wanted to link somebody without
 *       satisfying them would have to add a caller here, and this test is what it would trip over.
 *   <li>Only {@code PersonLinkGateway} calls the repository's link statements, so Lineage has one
 *       door rather than several.
 *   <li>Nothing outside the linker reaches the port's writes at all, including inside Linking.
 * </ol>
 *
 * <p>The self-service statements are held to the same rule for the same reason: doc 04 §6's
 * whitelist is only a whitelist while there is one way through it.
 */
class LinkWriterTest {

    /** The methods that change {@code persons.linked_user_id} through the published port. */
    private static final Set<String> PORT_WRITES = Set.of("linkIfUnlinked", "unlink");

    /** The repository statements underneath it, plus the two self-service ones. */
    private static final Set<String> REPOSITORY_WRITES =
            Set.of(
                    "linkUserIfUnlinked",
                    "unlinkUser",
                    "updateSelfServiceFields",
                    "updatePropagatedFields");

    /** The one class allowed to drive the port. */
    private static final String THE_LINKER = PersonUserLinker.class.getName();

    /** The one class allowed to drive the repository statements. */
    private static final String THE_GATEWAY = "app.giaky.lineage.application.PersonLinkGateway";

    /**
     * Adapters are plumbing, not callers.
     *
     * <p>{@code LineagePersonLinkAdapter} implements the port by delegating to the gateway, and the
     * in-memory double in the test tree implements the repository. Neither decides anything, and a
     * rule that could not tell them apart from a service would be a rule nobody could satisfy.
     */
    private static final Set<String> ADAPTERS =
            Set.of(
                    "app.giaky.linking.infrastructure.LineagePersonLinkAdapter",
                    "app.giaky.lineage.infrastructure.PersonRepositoryAdapter");

    private static JavaClasses productionClasses;

    @BeforeAll
    static void importProductionClasses() {
        productionClasses =
                new ClassFileImporter()
                        .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                        .importPackages("app.giaky");
    }

    @Test
    @DisplayName("only PersonUserLinker writes the link through PersonLinkPort")
    void onlyTheLinkerDrivesThePort() {
        assertThat(callersOf(PersonLinkPort.class.getName(), PORT_WRITES))
                .as(
                        "any other caller would be a second place I-P4, I-P5 and I-K1 have to be"
                            + " enforced, and the second place is the one that forgets")
                .containsOnly(THE_LINKER);
    }

    @Test
    @DisplayName("only PersonLinkGateway issues the link and self-service statements")
    void onlyTheGatewayDrivesTheRepository() {
        assertThat(callersOf(PersonRepository.class.getName(), REPOSITORY_WRITES))
                .as("Lineage exposes one door onto these columns, and this is it")
                .containsOnly(THE_GATEWAY);
    }

    /**
     * Every production class that calls one of {@code methods} on {@code owner}, minus the adapters
     * that merely implement the interfaces.
     */
    private static Set<String> callersOf(String owner, Set<String> methods) {
        return productionClasses.stream()
                .filter(type -> !ADAPTERS.contains(type.getName()))
                .filter(type -> !owner.equals(type.getName()))
                .filter(type -> callsAny(type, owner, methods))
                .map(JavaClass::getName)
                .collect(Collectors.toSet());
    }

    private static boolean callsAny(JavaClass type, String owner, Set<String> methods) {
        List<JavaMethodCall> calls = List.copyOf(type.getMethodCallsFromSelf());
        return calls.stream()
                .anyMatch(
                        call ->
                                owner.equals(call.getTargetOwner().getName())
                                        && methods.contains(call.getName()));
    }
}
