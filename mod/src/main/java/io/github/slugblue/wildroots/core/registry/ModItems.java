package io.github.slugblue.wildroots.core.registry;

import io.github.slugblue.wildroots.WildRoots;
import io.github.slugblue.wildroots.item.SeedLotItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/**
 * Items (WR-120; docs/05-architecture.md §3, `core.registry`).
 *
 * <p>Registrations exist always, independent of waves and config (§9.1):
 * the entries of a line arrive in the wave that ships its pack.
 */
public final class ModItems {

    public static final DeferredRegister<Item> REGISTER = DeferredRegister.create(ForgeRegistries.ITEMS, WildRoots.MOD_ID);

    /** {@code wildroots:seed_lot}: seeds of one mother plant, every species (WR-135; GDD §5.3). Core content. */
    public static final RegistryObject<SeedLotItem> SEED_LOT = REGISTER.register("seed_lot",
            () -> new SeedLotItem(new Item.Properties()));

    private ModItems() {
    }
}
