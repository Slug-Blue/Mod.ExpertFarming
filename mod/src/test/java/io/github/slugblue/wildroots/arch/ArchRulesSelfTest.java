package io.github.slugblue.wildroots.arch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.EvaluationResult;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

/**
 * WR-012: every architecture rule is proven to fire on a class that breaks
 * it (fixtures in {@code io.github.slugblue.wildroots.archfixtures}) and to
 * stay quiet on the allowed variant.
 */
class ArchRulesSelfTest {

    private static final String F = "io.github.slugblue.wildroots.archfixtures";

    private static JavaClasses classes(String... simpleNames) {
        Class<?>[] loaded = Stream.of(simpleNames).map(ArchRulesSelfTest::load).toArray(Class<?>[]::new);
        return new ClassFileImporter().importClasses(loaded);
    }

    private static Class<?> load(String relativeName) {
        try {
            return Class.forName(F + "." + relativeName, false, ArchRulesSelfTest.class.getClassLoader());
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void assertViolates(ArchRule rule, String... fixtures) {
        EvaluationResult result = rule.evaluate(classes(fixtures));
        assertTrue(result.hasViolation(), () -> "expected a violation of '" + rule.getDescription()
                + "' in " + List.of(fixtures));
    }

    private static void assertAllows(ArchRule rule, String... fixtures) {
        // an allowed class may also fall outside the rule's that() clause
        EvaluationResult result = rule.allowEmptyShould(true).evaluate(classes(fixtures));
        assertFalse(result.hasViolation(), () -> "unexpected violation: " + result.getFailureReport());
    }

    // ---------------------------------------------------------------- layering

    @Test
    void clientRuleFiresOutsideClientPackage() {
        ArchRule rule = ArchRules.noClientCodeOutsideClientPackage(F, List.of(F + ".FakeClientPlugin"));
        assertViolates(rule, "feature.UsesClientClass");
        assertAllows(rule, "client.ClientOnly", "feature.AnnotatedClientPlugin");
    }

    @Test
    void compatRuleFiresOnCompatAndForeignModImports() {
        ArchRule rule = ArchRules.compatIsolated(F, List.of(F + ".foreignmod.."));
        assertViolates(rule, "feature.UsesCompat");
        assertViolates(rule, "feature.UsesForeignMod");
        assertAllows(rule, "compat.fd.FdHooks");
    }

    @Test
    void geneticsRulesFireOnModAndMinecraftDependencies() {
        assertViolates(ArchRules.geneticsDoesNotDependOnMod(F), "genetics.UsesMod");
        assertAllows(ArchRules.geneticsDoesNotDependOnMod(F), "genetics.PlainJava");
        assertViolates(ArchRules.geneticsUsesOnlyJdk(F), "genetics.UsesMinecraft");
        assertViolates(ArchRules.geneticsUsesOnlyJdk(F), "genetics.UsesMod");
        assertAllows(ArchRules.geneticsUsesOnlyJdk(F), "genetics.PlainJava");
    }

    // ------------------------------------------------- NoNetworkNoExec (SEC-43)

    /** Fixture per rule, in the order of {@link ArchRules#noNetworkNoExec(String)}. */
    private static final List<String> NETWORK_FIXTURES = List.of(
            "net.UsesSocket", "net.UsesUriToUrl", "net.UsesHttpClient", "net.UsesJavaxNet",
            "net.UsesNettyBootstrap", "net.UsesRuntimeExec", "net.UsesProcessBuilder",
            "net.UsesDefineClass$Loader", "net.UsesUrlClassLoader", "net.UsesLookupDefineClass",
            "net.UsesScriptEngine", "net.UsesObjectStreams", "net.UsesLoadLibrary", "net.UsesUnsafe",
            "net.UsesClassForName", "net.UsesSetAccessible", "net.UsesPrivateLookupIn");

    @TestFactory
    Stream<DynamicTest> everyNoNetworkNoExecRuleFiresOnItsFixture() {
        List<ArchRule> rules = ArchRules.noNetworkNoExec(F);
        assertEquals(rules.size(), NETWORK_FIXTURES.size(), "one fixture per SEC-43 rule");
        return IntStream.range(0, rules.size()).mapToObj(i -> DynamicTest.dynamicTest(
                rules.get(i).getDescription() + " <- " + NETWORK_FIXTURES.get(i),
                () -> assertViolates(rules.get(i), NETWORK_FIXTURES.get(i))));
    }

    @Test
    void uriAsAValueIsAllowed() {
        assertAllows(ArchRules.noNetworkNoExec(F).get(0), "net.UsesUriValue");
        assertAllows(ArchRules.noNetworkNoExec(F).get(1), "net.UsesUriValue");
    }

    // ----------------------------------------------------------- templates

    @Test
    void packetConsumerRuleFiresOutsideTheGuard() {
        ArchRule rule = ArchRules.packetConsumersOnlyFrom(F, Set.of(F + ".network.FakeServerPacketGuard"));
        assertViolates(rule, "network.RegistersWithoutGuard");
        assertAllows(rule, "network.FakeServerPacketGuard");
    }

    @Test
    void worldSecretRuleFiresInNetworkPackage() {
        ArchRule rule = ArchRules.worldSecretHidden(F, F + ".core.safety.FakeWorldSecret");
        assertViolates(rule, "network.UsesWorldSecret");
    }

    @Test
    void loadedLevelAccessRuleFiresOnDirectReads() {
        ArchRule rule = ArchRules.levelReadsThroughLoadedLevelAccess(F, Set.of(F + ".plant.FakeLoadedLevelAccess"));
        assertViolates(rule, "plant.ReadsNeighbour");
        assertAllows(rule, "plant.FakeLoadedLevelAccess");
    }

    @Test
    void componentSerializerRuleFiresOnFromJson() {
        assertViolates(ArchRules.noComponentSerializerFromJson(F), "feature.ParsesJsonComponent");
    }
}
