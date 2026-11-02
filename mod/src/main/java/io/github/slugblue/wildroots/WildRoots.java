package io.github.slugblue.wildroots;

import com.mojang.logging.LogUtils;
import io.github.slugblue.wildroots.core.config.CommonConfig;
import io.github.slugblue.wildroots.core.config.ServerConfig;
import io.github.slugblue.wildroots.core.registry.ModRegistries;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

/**
 * Entry point of the Wild Roots mod (docs/05-architecture.md §3): attaches
 * the {@code DeferredRegister}s and registers the configs.
 */
@Mod(WildRoots.MOD_ID)
public final class WildRoots {

    /** Mod id; must match {@code mod_id} in gradle.properties and META-INF/mods.toml. */
    public static final String MOD_ID = "wildroots";

    private static final Logger LOGGER = LogUtils.getLogger();

    /** FML 47.4 injects the loading context (the static {@code get()} accessors are deprecated). */
    public WildRoots(FMLJavaModLoadingContext context) {
        IEventBus modBus = context.getModEventBus();
        ModRegistries.register(modBus);
        context.registerConfig(ModConfig.Type.COMMON, CommonConfig.SPEC);
        context.registerConfig(ModConfig.Type.SERVER, ServerConfig.SPEC);
        LOGGER.debug("Wild Roots constructed");
    }
}
