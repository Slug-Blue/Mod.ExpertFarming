package io.github.slugblue.wildroots.client.journal.scene;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * WR-181k (journal v2 §5.8): the scene engine draws without a world. Its
 * classes live in {@code ..client..}, never reach {@code ClientLevel},
 * {@code Level} or {@code Minecraft#level}, and the scene "world" is no
 * {@code Level}.
 */
class SceneArchitectureTest {

    static final String ROOT = "io.github.slugblue.wildroots";
    static final String SCENE = ROOT + ".client.journal.scene";
    private static JavaClasses classes;

    @BeforeAll
    static void importScene() {
        classes = new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(SCENE);
        assertTrue(classes.contain(SCENE + ".SceneBlockGetter"), "scene classes not found");
    }

    @Test
    void sceneClassesAreClientClasses() {
        classes().that().resideInAPackage(SCENE + "..").should().resideInAPackage(ROOT + "..client..").check(classes);
    }

    @Test
    void noWorldIsReached() {
        noClasses().that().resideInAPackage(SCENE + "..").should().dependOnClassesThat()
                .areAssignableTo("net.minecraft.world.level.Level")
                .orShould().dependOnClassesThat().areAssignableTo("net.minecraft.client.multiplayer.ClientLevel")
                .orShould().accessField("net.minecraft.client.Minecraft", "level")
                .check(classes);
    }

    @Test
    void entitiesAreNotCreated() {
        // the bee is a model: no Bee instance, no EntityType, no entity renderer
        noClasses().that().resideInAPackage(SCENE + "..").should().callConstructorWhere(
                com.tngtech.archunit.core.domain.JavaCall.Predicates.target(
                        com.tngtech.archunit.core.domain.properties.HasOwner.Predicates.With.owner(
                                com.tngtech.archunit.core.domain.JavaClass.Predicates.assignableTo(
                                        "net.minecraft.world.entity.Entity"))))
                .orShould().dependOnClassesThat().areAssignableTo("net.minecraft.world.entity.EntityType")
                .check(classes);
    }
}
