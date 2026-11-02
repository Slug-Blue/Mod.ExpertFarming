package io.github.slugblue.wildroots.core.registry;

import io.github.slugblue.wildroots.WildRoots;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElementType;
import net.minecraftforge.registries.DeferredRegister;

/**
 * Jigsaw pool element types (WR-120; docs/05-architecture.md §3, `core.registry`).
 *
 * <p>Registrations exist always, independent of waves and config (§9.1):
 * the entries of a line arrive in the wave that ships its pack.
 */
public final class ModPoolElements {

    public static final DeferredRegister<StructurePoolElementType<?>> REGISTER = DeferredRegister.create(Registries.STRUCTURE_POOL_ELEMENT, WildRoots.MOD_ID);

    private ModPoolElements() {
    }
}
