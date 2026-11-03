package io.github.slugblue.wildroots.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** WR-135, SEC-21: {@code LotStacks} is the only lot factory and the only source of {@code rare_source}. */
class LotStacksTest {

    /** The block entity rule of SEC-21: a mutant gives the flag once ({@code rareClaimed}). */
    static final class Claim implements LotStacks.RareClaim {
        final boolean mutant;
        boolean claimed;

        Claim(boolean mutant) {
            this.mutant = mutant;
        }

        @Override
        public boolean claimRare() {
            if (!mutant || claimed) {
                return false;
            }
            claimed = true;
            return true;
        }
    }

    static LotStacks.Parent mother(int flags) {
        return new LotStacks.Parent(LotNbtTest.SP, Optional.empty(), LotNbtTest.genome(1), Optional.of("Line"), 4, flags);
    }

    static LotNbt.Lot seeds(LotStacks.Parent p, LotStacks.RareClaim claim) {
        return assertInstanceOf(LotNbt.Lot.class,
                LotNbt.read(LotStacks.seedsTag(p, LotNbtTest.fathers(2), 0.5f, 0, 1L, claim)));
    }

    @Test
    void mutantGivesTheFlagOnceAndOnlyOnce() {
        Claim c = new Claim(true);
        assertEquals(LotNbt.RareSource.MUTATION, seeds(mother(0), c).rare());
        assertEquals(LotNbt.RareSource.NONE, seeds(mother(0), c).rare(), "rareClaimed must stop a second flag");
        assertEquals(LotNbt.RareSource.NONE, seeds(mother(0), new Claim(false)).rare());
    }

    @Test
    void descendantsDoNotInheritTheFlag() {
        LotNbt.Lot rare = seeds(mother(LotFlags.FROM_WILD), new Claim(true));
        LotStacks.Parent child = new LotStacks.Parent(rare.species(), rare.cultivar(), rare.mother(), rare.lineName(),
                rare.generation(), rare.flags());
        assertEquals(LotNbt.RareSource.NONE, seeds(child, LotStacks.RareClaim.NONE).rare());
        LotNbt.Lot seedling = assertInstanceOf(LotNbt.Lot.class,
                LotNbt.read(LotStacks.seedlingTag(child, new byte[] {1, 2}, 2L)));
        assertEquals(LotNbt.RareSource.NONE, seedling.rare());
        assertThrows(IllegalArgumentException.class,
                () -> LotStacks.lazyTag(LotNbtTest.SP, "meadow", 1, 2, LotNbt.RareSource.MUTATION));
    }

    @Test
    void lineNameGenerationAndWildMarkFollowTheMother() {
        LotNbt.Lot lot = seeds(mother(LotFlags.FROM_WILD | LotFlags.BEES | LotFlags.INSPECTED), LotStacks.RareClaim.NONE);
        assertEquals(Optional.of("Line"), lot.lineName());
        assertEquals(5, lot.generation());
        assertEquals(LotFlags.FROM_WILD, lot.flags(), "only the mother-line flags are inherited");
    }

    /** Only {@code LotStacks} calls the lot writers of {@code LotNbt}, and they are not public. */
    @Test
    void lotStacksIsTheOnlyFactory() {
        JavaClasses classes = new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("io.github.slugblue.wildroots");
        Set<String> callers = classes.stream()
                .flatMap(c -> c.getMethodCallsFromSelf().stream())
                .filter(call -> call.getTargetOwner().getName().equals(LotNbt.class.getName())
                        && call.getTarget().getName().equals("write"))
                .map(JavaMethodCall::getOriginOwner)
                .map(o -> o.getName())
                .collect(Collectors.toSet());
        assertEquals(Set.of(LotStacks.class.getName()), callers);
        assertTrue(Arrays.stream(LotNbt.class.getDeclaredMethods())
                .filter(m -> m.getName().equals("write"))
                .noneMatch(m -> Modifier.isPublic(m.getModifiers())), "LotNbt.write is public");
    }
}
