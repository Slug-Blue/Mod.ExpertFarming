package io.github.slugblue.wildroots.arch;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.assignableTo;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.type;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaCall;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.properties.HasName;
import com.tngtech.archunit.lang.ArchRule;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.AccessibleObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Architecture rules of Wild Roots (WR-012; docs/05-architecture.md §3, §15,
 * §17; SEC-01, SEC-07, SEC-25, SEC-32, SEC-43).
 *
 * <p>Every rule takes the root package it guards, so the same rule runs on
 * the production classes ({@link ProductionArchitectureTest}) and on the
 * violating fixtures ({@link ArchRulesSelfTest}).
 */
public final class ArchRules {

    /** Root package of the mod and of the engine. */
    public static final String ROOT = "io.github.slugblue.wildroots";

    /** Packages of the optional integrations (docs/05-architecture.md §13). */
    public static final List<String> FOREIGN_MOD_PACKAGES = List.of(
            "vectorwing.farmersdelight..", "mezz.jei..", "snownee.jade..", "dev.emi..", "mcp.mobius.waila..");

    /** Annotations of client-side integration plugins that may touch client classes. */
    public static final List<String> CLIENT_PLUGIN_ANNOTATIONS = List.of(
            "mezz.jei.api.JeiPlugin", "snownee.jade.api.WailaPlugin", "dev.emi.emi.api.EmiEntrypoint",
            "mcp.mobius.waila.api.WailaPlugin");

    private ArchRules() {
    }

    // ---------------------------------------------------------------- layering

    /** No {@code net.minecraft.client} outside {@code ..client..} and annotated client plugins (§15). */
    public static ArchRule noClientCodeOutsideClientPackage(String root, List<String> clientPluginAnnotations) {
        return noClasses()
                .that().resideInAPackage(root + "..")
                .and().resideOutsideOfPackage(root + "..client..")
                .and(not(annotatedWithAny(clientPluginAnnotations)))
                .should().dependOnClassesThat().resideInAPackage("net.minecraft.client..")
                .because("client code lives in ..client.. (docs/05-architecture.md §15)");
    }

    /** Only {@code compat} imports {@code compat} and other mods (§3). */
    public static ArchRule compatIsolated(String root, List<String> foreignModPackages) {
        List<String> targets = new ArrayList<>();
        targets.add(root + ".compat..");
        targets.addAll(foreignModPackages);
        return noClasses()
                .that().resideInAPackage(root + "..")
                .and().resideOutsideOfPackage(root + ".compat..")
                .should().dependOnClassesThat().resideInAnyPackage(targets.toArray(String[]::new))
                .because("integrations are isolated in compat (docs/05-architecture.md §3)");
    }

    /** {@code :genetics} does not depend on {@code :mod} (ADR-0001). */
    public static ArchRule geneticsDoesNotDependOnMod(String root) {
        return noClasses()
                .that().resideInAPackage(root + ".genetics..")
                .should().dependOnClassesThat(resideInAPackage(root + "..")
                        .and(not(resideInAPackage(root + ".genetics.."))))
                .because(":genetics is independent of :mod (ADR-0001)");
    }

    /** {@code :genetics} uses only the JDK and itself: no Minecraft, Forge or libraries (ADR-0001). */
    public static ArchRule geneticsUsesOnlyJdk(String root) {
        return classes()
                .that().resideInAPackage(root + ".genetics..")
                .should().onlyDependOnClassesThat().resideInAnyPackage(root + ".genetics..", "java..")
                .because(":genetics is plain Java (ADR-0001)");
    }

    // ------------------------------------------------- NoNetworkNoExec (SEC-43)

    /** SEC-43: one rule per forbidden API, in the order of the SEC-43 list. */
    public static List<ArchRule> noNetworkNoExec(String root) {
        return List.of(
                noDependency(root, "java.net (URI as a value is allowed)",
                        resideInAPackage("java.net..").and(not(type(java.net.URI.class)))),
                noCall(root, "URI#toURL", callTo(java.net.URI.class, "toURL")),
                noDependency(root, "java.net.http", resideInAPackage("java.net.http..")),
                noDependency(root, "javax.net", resideInAPackage("javax.net..")),
                noDependency(root, "io.netty.bootstrap", resideInAPackage("io.netty.bootstrap..")),
                noCall(root, "Runtime#exec", callTo(Runtime.class, "exec")),
                noDependency(root, "ProcessBuilder", assignableTo(ProcessBuilder.class)),
                noCall(root, "ClassLoader#defineClass", callTo(ClassLoader.class, "defineClass")),
                noDependency(root, "URLClassLoader", assignableTo(java.net.URLClassLoader.class)),
                noCall(root, "MethodHandles.Lookup#defineClass",
                        callTo(MethodHandles.Lookup.class, "defineClass", "defineHiddenClass")),
                noDependency(root, "javax.script", resideInAPackage("javax.script..")),
                noDependency(root, "ObjectInputStream / ObjectOutputStream",
                        assignableTo(java.io.ObjectInputStream.class)
                                .or(assignableTo(java.io.ObjectOutputStream.class))),
                noCall(root, "System#load / System#loadLibrary",
                        callTo(System.class, "load", "loadLibrary").or(callTo(Runtime.class, "load", "loadLibrary"))),
                noDependency(root, "sun.misc.Unsafe",
                        HasName.Predicates.name("sun.misc.Unsafe").or(HasName.Predicates.name("jdk.internal.misc.Unsafe"))),
                noCall(root, "Class#forName", callTo(Class.class, "forName")),
                noCall(root, "AccessibleObject#setAccessible",
                        callTo(AccessibleObject.class, "setAccessible", "trySetAccessible")),
                noCall(root, "MethodHandles#privateLookupIn", callTo(MethodHandles.class, "privateLookupIn")));
    }

    // ----------------------------------------- templates, switched on later

    /**
     * SEC-01 (switched on by WR-127): only the guard and the S→C registrar
     * register packet consumers on a {@code SimpleChannel}.
     */
    public static ArchRule packetConsumersOnlyFrom(String root, Set<String> allowedClasses) {
        return noClasses()
                .that().resideInAPackage(root + "..")
                .and(not(fullyQualifiedNameIn(allowedClasses)))
                .should().callMethodWhere(
                        callTo("net.minecraftforge.network.simple.SimpleChannel", "registerMessage")
                                .or(callTo("net.minecraftforge.network.simple.SimpleChannel$MessageBuilder",
                                        "consumerMainThread", "consumerNetworkThread")))
                .because("C→S handlers are registered only through ServerPacketGuard (SEC-01)");
    }

    /** SEC-25 (switched on by WR-123): the world secret is not reachable from network, client or compat. */
    public static ArchRule worldSecretHidden(String root, String worldSecretClass) {
        return noClasses()
                .that().resideInAnyPackage(root + "..network..", root + "..client..", root + "..compat..")
                .should().dependOnClassesThat().haveFullyQualifiedName(worldSecretClass)
                .allowEmptyShould(true)
                .because("the world secret never leaves the server logic (SEC-25)");
    }

    /**
     * SEC-32 (switched on by WR-121): plant, pollination, village and compat
     * read blocks and block entities only through LoadedLevelAccess.
     */
    public static ArchRule levelReadsThroughLoadedLevelAccess(String root, Set<String> allowedClasses) {
        return noClasses()
                .that().resideInAnyPackage(root + ".plant..", root + ".pollination..", root + ".village..",
                        root + ".compat..")
                .and(not(fullyQualifiedNameIn(allowedClasses)))
                .should().callMethodWhere(
                        callTo("net.minecraft.world.level.BlockGetter", "getBlockState", "getBlockEntity")
                                .or(callTo("net.minecraft.world.level.LevelReader", "getChunk")))
                .allowEmptyShould(true)
                .because("neighbour reads must not load chunks (SEC-32)");
    }

    /** SEC-07 (switched on by WR-122): player strings are never parsed as JSON components. */
    public static ArchRule noComponentSerializerFromJson(String root) {
        DescribedPredicate<JavaCall<?>> fromJson = DescribedPredicate.describe(
                "call to Component.Serializer#fromJson*",
                call -> call.getTargetOwner().getName().equals("net.minecraft.network.chat.Component$Serializer")
                        && call.getTarget().getName().startsWith("fromJson"));
        return noClasses()
                .that().resideInAPackage(root + "..")
                .should().callMethodWhere(fromJson)
                .because("player text is only Component.literal (SEC-07)");
    }

    // ------------------------------------------------------------------ helpers

    private static ArchRule noDependency(String root, String what, DescribedPredicate<? super JavaClass> target) {
        return noClasses()
                .that().resideInAPackage(root + "..")
                .should().dependOnClassesThat(target)
                .as("NoNetworkNoExec: no " + what + " (SEC-43)");
    }

    private static ArchRule noCall(String root, String what, DescribedPredicate<? super JavaCall<?>> target) {
        return noClasses()
                .that().resideInAPackage(root + "..")
                .should().callMethodWhere(target)
                .as("NoNetworkNoExec: no " + what + " (SEC-43)");
    }

    /** A call to one of {@code names} declared on {@code owner} or on a subtype of it. */
    static DescribedPredicate<JavaCall<?>> callTo(Class<?> owner, String... names) {
        return callTo(owner.getName(), names);
    }

    static DescribedPredicate<JavaCall<?>> callTo(String ownerName, String... names) {
        Set<String> wanted = Set.of(names);
        return DescribedPredicate.describe("call to " + ownerName + "#" + String.join("|", names),
                call -> wanted.contains(call.getTarget().getName())
                        && call.getTargetOwner().isAssignableTo(ownerName));
    }

    private static DescribedPredicate<JavaClass> annotatedWithAny(List<String> annotations) {
        return DescribedPredicate.describe("annotated with one of " + annotations,
                c -> annotations.stream().anyMatch(c::isAnnotatedWith));
    }

    private static DescribedPredicate<JavaClass> fullyQualifiedNameIn(Set<String> names) {
        return DescribedPredicate.describe("one of " + names, c -> names.contains(c.getName()));
    }
}
