package io.github.slugblue.wildroots.core.registry;

import io.github.slugblue.wildroots.WildRoots;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.DeferredRegister;

/**
 * Items (WR-120; docs/05-architecture.md §3, `core.registry`).
 *
 * <p>Registrations exist always, independent of waves and config (§9.1):
 * the entries of a line arrive in the wave that ships its pack.
 */
public final class ModItems {

    public static final DeferredRegister<Item> REGISTER = DeferredRegister.create(ForgeRegistries.ITEMS, WildRoots.MOD_ID);

    private ModItems() {
    }
}
