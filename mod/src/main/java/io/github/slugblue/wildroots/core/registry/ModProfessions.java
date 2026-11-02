package io.github.slugblue.wildroots.core.registry;

import io.github.slugblue.wildroots.WildRoots;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.DeferredRegister;

/**
 * Villager professions (WR-120; docs/05-architecture.md §3, `core.registry`).
 *
 * <p>Registrations exist always, independent of waves and config (§9.1):
 * the entries of a line arrive in the wave that ships its pack.
 */
public final class ModProfessions {

    public static final DeferredRegister<VillagerProfession> REGISTER = DeferredRegister.create(ForgeRegistries.VILLAGER_PROFESSIONS, WildRoots.MOD_ID);

    private ModProfessions() {
    }
}
