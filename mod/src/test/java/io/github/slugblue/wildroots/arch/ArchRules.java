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
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
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
                .should().dependOnClassesThat().resideInAnyPackage(CLIENT_ONLY_PACKAGES.toArray(String[]::new))
                .because("client code lives in ..client.. (docs/05-architecture.md §15)");
    }

    /** Vanilla client-only packages: the game client and the render stack (WR-198, §15). */
    public static final List<String> CLIENT_ONLY_PACKAGES = List.of("net.minecraft.client..", "com.mojang.blaze3d..");

    /**
     * WR-198 (§15): common code reaches the mod's own {@code ..client..}
     * classes only from inside a DistExecutor-guarded lambda body (the
     * {@code DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> …)}
     * pattern), so a dedicated server never loads them. A plain lambda (a
     * work item, a listener) runs on the server too and is a violation. A field, parameter,
     * return or super type, or a call, method reference or class literal in
     * an ordinary method is a violation: the JVM resolves those when the
     * common class is verified or the method runs.
     *
     * <p>ArchUnit attributes a lambda's accesses to the enclosing method, so
     * the condition reads the bytecode itself ({@link ClientReferences}, ASM
     * from Forge's classpath): every reference to a client class must sit in
     * a synthetic {@code lambda$…} method created right before a
     * {@code DistExecutor} call, or inside such a lambda.
     */
    public static ArchRule modClientOnlyFromLambdas(String root) {
        String client = root.replace('.', '/') + "/client/";
        ArchCondition<JavaClass> onlyFromLambdas = new ArchCondition<>("reach " + root + "..client.. only from DistExecutor lambda bodies") {
            @Override
            public void check(JavaClass c, ConditionEvents events) {
                for (String v : ClientReferences.outsideLambdas(c.reflect(), client)) {
                    events.add(SimpleConditionEvent.violated(c, c.getName() + ": " + v));
                }
            }
        };
        return classes()
                .that().resideInAPackage(root + "..")
                .and().resideOutsideOfPackage(root + "..client..")
                .should(onlyFromLambdas)
                .because("a dedicated server must never load a client class (docs/05-architecture.md §15)");
    }

    /**
     * WR-181 (§15): screens ({@code net.minecraft.client.gui.screens.Screen}
     * subclasses) and screen helpers named {@code *Screen} live in
     * {@code ..client..} only.
     */
    public static ArchRule screensOnlyInClient(String root) {
        return classes()
                .that().resideInAPackage(root + "..")
                .and().areAssignableTo("net.minecraft.client.gui.screens.Screen")
                .or().haveSimpleNameEndingWith("Screen").and().resideInAPackage(root + "..")
                .should().resideInAPackage(root + "..client..")
                .allowEmptyShould(true)
                .because("screens are client code (docs/05-architecture.md §15)");
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

    /**
     * WR-101: the engine draws randomness only from its own SplitMix64 and
     * hashes only with Hash64 (ADR-0001, docs/05-architecture.md §4.3): no
     * {@code java.util.Random} and subclasses, {@code SplittableRandom},
     * {@code Math.random}, {@code String.hashCode}.
     */
    public static ArchRule geneticsDeterministicRandomOnly(String root) {
        return noClasses()
                .that().resideInAPackage(root + ".genetics..")
                .should().dependOnClassesThat(assignableTo(java.util.Random.class)
                        .or(type(java.util.SplittableRandom.class)))
                .orShould().callMethodWhere(callTo(Math.class, "random")
                        .or(callTo(StrictMath.class, "random"))
                        .or(callTo(String.class, "hashCode")))
                .because("the engine is deterministic across JVMs (ADR-0001)");
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

    /** Field types a C→S packet may carry: identifiers and strings only (SEC-04, WR-127). */
    public static final Set<String> C2S_FIELD_TYPES = Set.of("long", "int", "boolean", "java.lang.String",
            "net.minecraft.resources.ResourceLocation");

    /**
     * SEC-04 (WR-127): instance fields of C→S packets ({@code ..network.c2s..})
     * are only {@code long}, {@code int}, {@code boolean}, {@code String},
     * {@code ResourceLocation}: the client sends an intent, never genomes,
     * stacks, amounts or flags.
     */
    public static ArchRule c2sPacketsCarryOnlyIntents(String root) {
        return com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields()
                .that().areDeclaredInClassesThat().resideInAPackage(root + "..network.c2s..")
                .and().areNotStatic()
                .should().haveRawType(DescribedPredicate.describe("one of " + C2S_FIELD_TYPES,
                        (JavaClass c) -> C2S_FIELD_TYPES.contains(c.getName())))
                .allowEmptyShould(true)
                .because("C→S packets carry only the player's intent (SEC-04)");
    }

    /**
     * SEC-34: every method annotated {@code @EntryPoint} calls one of the
     * wrapping methods of {@code Guarded} itself ({@link #GUARDED_WRAPPERS});
     * other calls such as {@code Guarded.shared()} do not count.
     */
    /** Methods of {@code Guarded} that run a body in isolation (static and instance forms). */
    public static final Set<String> GUARDED_WRAPPERS = Set.of("run", "call", "execute", "compute");

    public static ArchRule entryPointsAreGuarded(String root, String annotation, String guardedClass) {
        ArchCondition<JavaMethod> callsGuarded = new ArchCondition<>("call " + guardedClass) {
            @Override
            public void check(JavaMethod method, ConditionEvents events) {
                boolean ok = method.getMethodCallsFromSelf().stream()
                        .anyMatch(c -> c.getTargetOwner().getName().equals(guardedClass)
                                && GUARDED_WRAPPERS.contains(c.getTarget().getName()));
                if (!ok) {
                    events.add(SimpleConditionEvent.violated(method,
                            method.getFullName() + " is an @EntryPoint but does not call " + guardedClass));
                }
            }
        };
        return com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods()
                .that().areAnnotatedWith(annotation)
                .and().areDeclaredInClassesThat().resideInAPackage(root + "..")
                .should(callsGuarded)
                .allowEmptyShould(true)
                .because("entry points isolate exceptions (SEC-34)");
    }

    /**
     * SEC-04: C→S packets are registered with {@code ServerPacketGuard.register}
     * only from {@code ..network..}, where the registrations are reviewed; the
     * guard itself refuses types outside {@code ..network.c2s..} at run time.
     */
    public static ArchRule c2sRegistrationOnlyInNetwork(String root, String guardClass) {
        return noClasses()
                .that().resideInAPackage(root + "..")
                .and().resideOutsideOfPackage(root + "..network..")
                .should().callMethodWhere(callTo(guardClass, "register"))
                .because("C→S packets are registered in the network package only (SEC-04)");
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

    /**
     * ADR-0008 (WR-140): the bloom index lives in memory only; nothing in
     * {@code ..pollination..} is {@code SavedData}, so it is never written to disk.
     */
    public static ArchRule bloomIndexNotSaved(String root) {
        return noClasses()
                .that().resideInAPackage(root + ".pollination..")
                .should().beAssignableTo("net.minecraft.world.level.saveddata.SavedData")
                .allowEmptyShould(true)
                .because("the bloom index is transient and rebuilds from onLoad (ADR-0008)");
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
