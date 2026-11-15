package io.github.slugblue.wildroots.gametest;

import io.github.slugblue.wildroots.core.Ids;
import io.github.slugblue.wildroots.WildRoots;
import io.github.slugblue.wildroots.core.config.ServerConfig;
import io.github.slugblue.wildroots.core.registry.ModCreativeTabs;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** WR-120: registries, creative tab and configs on a running server. */
@GameTestHolder(WildRoots.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CoreRegistryGameTests {

    private CoreRegistryGameTests() {
    }

    @GameTest(template = SmokeGameTests.FLOOR_3X3X3)
    public static void creativeTabIsRegistered(GameTestHelper helper) {
        CreativeModeTab tab = BuiltInRegistries.CREATIVE_MODE_TAB.get(Ids.mod(WildRoots.MOD_ID));
        helper.assertTrue(tab != null && tab == ModCreativeTabs.WILDROOTS.get(), "creative tab wildroots:wildroots missing");
        helper.succeed();
    }

    @GameTest(template = SmokeGameTests.FLOOR_3X3X3)
    public static void serverConfigIsLoadedWithDefaults(GameTestHelper helper) {
        helper.assertTrue(ServerConfig.SPEC.isLoaded(), "SERVER config is not loaded");
        helper.assertTrue(ServerConfig.MODE.get() == ServerConfig.Mode.STRICT, "mode is not strict by default");
        helper.assertTrue(ServerConfig.BREEDER_RARE_FIND_DAILY_CAP.get() == 6, "breederRareFindDailyCap is not 6");
        helper.succeed();
    }

    /**
     * Regression (2026-11-15): a test changes a SERVER value through an
     * override that never writes the file, so Forge's file watcher cannot
     * reload a stale value into the tests after it. Own batch: it changes
     * the mode for a moment.
     */
    @GameTest(template = SmokeGameTests.FLOOR_3X3X3, batch = "cfg_override")
    public static void aTestOverrideLeavesTheConfigAlone(GameTestHelper helper) {
        try {
            ServerConfig.overrideForTests(ServerConfig.MODE, ServerConfig.Mode.SOFT);
            helper.assertTrue(ServerConfig.value(ServerConfig.MODE) == ServerConfig.Mode.SOFT, "override not seen");
            helper.assertTrue(ServerConfig.MODE.get() == ServerConfig.Mode.STRICT, "the override changed the config");
        } finally {
            ServerConfig.overrideForTests(ServerConfig.MODE, null);
        }
        helper.assertTrue(ServerConfig.value(ServerConfig.MODE) == ServerConfig.Mode.STRICT, "override not cleared");
        helper.succeed();
    }
}
