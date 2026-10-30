package io.github.slugblue.wildroots.arch;

import static io.github.slugblue.wildroots.arch.ArchRules.ROOT;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
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

    /** NoNetworkNoExecTest (SEC-43): one dynamic test per forbidden API. */
    @TestFactory
    Stream<DynamicTest> noNetworkNoExec() {
        return ArchRules.noNetworkNoExec(ROOT).stream()
                .map(rule -> DynamicTest.dynamicTest(rule.getDescription(), () -> rule.check(production)));
    }

    // Templates: switched on by the tasks that add the guarded classes.

    @Test
    @Disabled("WR-127 switches it on together with ServerPacketGuard and the S→C registrar")
    void packetConsumersOnlyFromGuard() {
        ArchRules.packetConsumersOnlyFrom(ROOT, Set.of(
                ROOT + ".network.ServerPacketGuard", ROOT + ".network.WrNetwork")).check(production);
    }

    @Test
    @Disabled("WR-123 switches it on together with core.safety.WorldSecret")
    void worldSecretHidden() {
        ArchRules.worldSecretHidden(ROOT, ROOT + ".core.safety.WorldSecret").check(production);
    }

    @Test
    @Disabled("WR-121 switches it on together with core.safety.LoadedLevelAccess")
    void levelReadsThroughLoadedLevelAccess() {
        ArchRules.levelReadsThroughLoadedLevelAccess(ROOT, Set.of(
                ROOT + ".core.safety.LoadedLevelAccess")).check(production);
    }

    @Test
    @Disabled("WR-122 switches it on together with core.safety.PlayerText")
    void noComponentSerializerFromJson() {
        ArchRules.noComponentSerializerFromJson(ROOT).check(production);
    }
}
