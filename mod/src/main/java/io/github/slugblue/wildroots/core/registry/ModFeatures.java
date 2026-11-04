package io.github.slugblue.wildroots.core.registry;

import io.github.slugblue.wildroots.WildRoots;
import io.github.slugblue.wildroots.worldgen.WildPatchFeature;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/**
 * Worldgen features (WR-120; docs/05-architecture.md §3, `core.registry`).
 *
 * <p>Registrations exist always, independent of waves and config (§9.1):
 * the entries of a line arrive in the wave that ships its pack.
 */
public final class ModFeatures {

    public static final DeferredRegister<Feature<?>> REGISTER = DeferredRegister.create(ForgeRegistries.FEATURES, WildRoots.MOD_ID);

    /** {@code wildroots:wild_patch} (WR-150; worldgen §3). */
    public static final RegistryObject<WildPatchFeature> WILD_PATCH = REGISTER.register("wild_patch",
            WildPatchFeature::new);

    private ModFeatures() {
    }
}
