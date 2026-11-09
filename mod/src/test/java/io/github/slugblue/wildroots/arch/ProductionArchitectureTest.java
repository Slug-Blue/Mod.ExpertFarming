package io.github.slugblue.wildroots.arch;

import static io.github.slugblue.wildroots.arch.ArchRules.ROOT;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

/**
 * WR-012: architecture rules on the production classes of {@code :mod} and
 * {@code :genetics}; test classes and fixtures are not imported.
 */
class ProductionArchitectureTest {

    private static JavaClasses production;

    @BeforeAll
    static void importProductionClasses() {
        production = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(ROOT);
    }

    /** WR-181: the journal screen and every later screen are client classes. */
    @Test
    void screensOnlyInClient() {
        ArchRules.screensOnlyInClient(ROOT).check(production);
        org.junit.jupiter.api.Assertions.assertTrue(production.contain(ROOT + ".client.JournalScreen"),
                "the journal screen is not where the rule looks");
    }

    @Test
    void noClientCodeOutsideClientPackage() {
        ArchRules.noClientCodeOutsideClientPackage(ROOT, ArchRules.CLIENT_PLUGIN_ANNOTATIONS).check(production);
    }

    @Test
    void compatIsolated() {
        ArchRules.compatIsolated(ROOT, ArchRules.FOREIGN_MOD_PACKAGES).check(production);
    }

    @Test
    void geneticsDoesNotDependOnMod() {
        ArchRules.geneticsDoesNotDependOnMod(ROOT).check(production);
    }

    @Test
    void geneticsUsesOnlyJdk() {
        ArchRules.geneticsUsesOnlyJdk(ROOT).check(production);
    }

    @Test
    void geneticsDeterministicRandomOnly() {
        ArchRules.geneticsDeterministicRandomOnly(ROOT).check(production);
    }

    /** NoNetworkNoExecTest (SEC-43): one dynamic test per forbidden API. */
    @TestFactory
    Stream<DynamicTest> noNetworkNoExec() {
        return ArchRules.noNetworkNoExec(ROOT).stream()
                .map(rule -> DynamicTest.dynamicTest(rule.getDescription(), () -> rule.check(production)));
    }

    // Templates: switched on by the tasks that add the guarded classes.

    /** SEC-01, switched on by WR-127. */
    @Test
    void packetConsumersOnlyFromGuard() {
        ArchRules.packetConsumersOnlyFrom(ROOT, Set.of(
                ROOT + ".network.ServerPacketGuard", ROOT + ".network.WrNetwork")).check(production);
    }

    /** SEC-34 (review 1 of batch 2). */
    @Test
    void entryPointsAreGuarded() {
        ArchRules.entryPointsAreGuarded(ROOT, ROOT + ".core.safety.EntryPoint", ROOT + ".core.safety.Guarded")
                .check(production);
    }

    /** SEC-04 (review 1 of batch 2). */
    @Test
    void c2sRegistrationOnlyInNetwork() {
        ArchRules.c2sRegistrationOnlyInNetwork(ROOT, ROOT + ".network.ServerPacketGuard").check(production);
    }

    /** SEC-04, WR-127. */
    @Test
    void c2sPacketsCarryOnlyIntents() {
        ArchRules.c2sPacketsCarryOnlyIntents(ROOT).check(production);
    }

    /** SEC-25, switched on by WR-123. */
    @Test
    void worldSecretHidden() {
        ArchRules.worldSecretHidden(ROOT, ROOT + ".core.safety.WorldSecret").check(production);
    }

    /** SEC-32, switched on by WR-121. */
    @Test
    void levelReadsThroughLoadedLevelAccess() {
        ArchRules.levelReadsThroughLoadedLevelAccess(ROOT, Set.of(
                ROOT + ".core.safety.LoadedLevelAccess")).check(production);
    }

    /** ADR-0008, WR-140: the bloom index has no SavedData. */
    @Test
    void bloomIndexNotSaved() {
        ArchRules.bloomIndexNotSaved(ROOT).check(production);
    }

    /** SEC-07, switched on by WR-122. */
    @Test
    void noComponentSerializerFromJson() {
        ArchRules.noComponentSerializerFromJson(ROOT).check(production);
    }
}
