package io.github.slugblue.wildroots;

import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

/**
 * Entry point of the Wild Roots mod (docs/05-architecture.md §3).
 *
 * <p>Phase 4 skeleton: no registries and no game mechanics yet.
 */
@Mod(WildRoots.MOD_ID)
public final class WildRoots {

    /** Mod id; must match {@code mod_id} in gradle.properties and META-INF/mods.toml. */
    public static final String MOD_ID = "wildroots";

    private static final Logger LOGGER = LogUtils.getLogger();

    public WildRoots() {
        LOGGER.debug("Wild Roots constructed");
    }
}
