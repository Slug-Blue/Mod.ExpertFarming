package io.github.slugblue.wildroots.gametest;

import io.github.slugblue.wildroots.WildRoots;
import io.github.slugblue.wildroots.core.config.ServerConfig;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The GameTest server runs a world with a random seed, and the GameTest areas lie in whatever biome that seed gives
 * there: a desert or a savanna (temperature 2.0) would wilt young plants of player origin in any test that ticks
 * them. So the GameTest server starts with {@code climate.wilting} off; the tests of wilting
 * ({@code ClimateStressGameTests}, the two M1a gate functions) switch it on for the span of
 * their own synchronous bodies ({@link ClimateKit#withWilting}). Dev and production keep the config's default.
 */
@Mod.EventBusSubscriber(modid = WildRoots.MOD_ID)
public final class ClimateGameTestDefaults {

    /** The value the GameTest server runs with unless a test says otherwise. */
    static final Boolean DEFAULT = Boolean.FALSE;

    private ClimateGameTestDefaults() {
    }

    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        if (event.getServer() instanceof GameTestServer) {
            ServerConfig.overrideForTests(ServerConfig.CLIMATE_WILTING, DEFAULT);
        }
    }
}
