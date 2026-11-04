package io.github.slugblue.wildroots.core.registry;

import io.github.slugblue.wildroots.WildRoots;
import io.github.slugblue.wildroots.worldgen.ChunkBandPlacement;
import io.github.slugblue.wildroots.worldgen.SpeciesClimatePlacement;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.placement.PlacementModifierType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/**
 * Placement modifier types of the wild patches (WR-150; worldgen §4.2):
 * {@code wildroots:chunk_band} and {@code wildroots:species_climate}.
 */
public final class ModPlacementModifiers {

    public static final DeferredRegister<PlacementModifierType<?>> REGISTER =
            DeferredRegister.create(Registries.PLACEMENT_MODIFIER_TYPE, WildRoots.MOD_ID);

    public static final RegistryObject<PlacementModifierType<ChunkBandPlacement>> CHUNK_BAND =
            REGISTER.register("chunk_band", () -> () -> ChunkBandPlacement.CODEC);
    public static final RegistryObject<PlacementModifierType<SpeciesClimatePlacement>> SPECIES_CLIMATE =
            REGISTER.register("species_climate", () -> () -> SpeciesClimatePlacement.CODEC);

    private ModPlacementModifiers() {
    }
}
