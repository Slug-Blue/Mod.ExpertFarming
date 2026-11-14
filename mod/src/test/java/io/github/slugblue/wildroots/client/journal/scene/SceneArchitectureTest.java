package io.github.slugblue.wildroots.client.journal.scene;

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

    /**
     * Review m9: what "scene classes are client classes" means and can fail
     * on: no class outside {@code ..client..} reaches the journal v2
     * ({@code ..client.journal..}, scenes included), so a dedicated server
     * never loads it. (ArchRules.modClientOnlyFromLambdas allows reaching
     * {@code ..client..} from DistExecutor lambdas; the journal is never
     * reached that way.)
     */
    static com.tngtech.archunit.lang.ArchRule journalOnlyFromClient() {
        return noClasses().that().resideOutsideOfPackage(ROOT + ".client..")
                .should().dependOnClassesThat().resideInAPackage(ROOT + ".client.journal..")
                .because("the journal v2 is client code (journal v2 §5.1)");
    }

    @Test
    void theJournalIsReachedOnlyFromClientCode() {
        JavaClasses production = new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(ROOT);
        journalOnlyFromClient().check(production);
    }

    @Test
    void theRuleFailsOnALeak() {
        JavaClasses leak = new ClassFileImporter().importClasses(
                io.github.slugblue.wildroots.archfixtures.JournalLeak.class,
                io.github.slugblue.wildroots.client.journal.GuideBook.class);
        org.junit.jupiter.api.Assertions.assertThrows(AssertionError.class, () -> journalOnlyFromClient().check(leak));
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
