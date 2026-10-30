package io.github.slugblue.wildroots.genetics;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.slugblue.wildroots.genetics.api.GeneticsService;
import org.junit.jupiter.api.Test;

/** WR-006: the engine is plain Java and Minecraft is not on its classpath (ADR-0001). */
class GeneticsIsolationTest {

    @Test
    void facadeIsAnInterface() {
        assertTrue(GeneticsService.class.isInterface());
    }

    @Test
    void minecraftIsNotOnTheClasspath() {
        ClassLoader loader = GeneticsIsolationTest.class.getClassLoader();
        assertThrows(ClassNotFoundException.class,
                () -> loader.loadClass("net.minecraft.world.level.Level"));
        assertThrows(ClassNotFoundException.class,
                () -> loader.loadClass("net.minecraftforge.fml.common.Mod"));
    }
}
