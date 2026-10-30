package io.github.slugblue.wildroots.gametest;

import io.github.slugblue.wildroots.WildRoots;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * WR-011: smoke GameTest, "the mod is loaded" (docs/05-architecture.md §17).
 *
 * <p>Templates live in {@code data/wildroots/structures/gametest/}; with
 * {@code @PrefixGameTestTemplate(false)} the template id is
 * {@code wildroots:gametest/<name>}.
 */
@GameTestHolder(WildRoots.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SmokeGameTests {

    /** 3x3x3 template: stone floor, air above. */
    static final String FLOOR_3X3X3 = "gametest/floor_3x3x3";

    private SmokeGameTests() {
    }

    @GameTest(template = FLOOR_3X3X3)
    public static void modIsLoaded(GameTestHelper helper) {
        helper.assertTrue(ModList.get().isLoaded(WildRoots.MOD_ID), "wildroots is not in the mod list");
        helper.succeed();
    }
}
