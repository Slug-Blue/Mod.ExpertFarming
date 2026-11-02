package io.github.slugblue.wildroots.core.registry;

import java.util.List;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;

/**
 * All {@code DeferredRegister}s of the mod, in registration order (WR-120;
 * docs/05-architecture.md §3). {@code ModConditions} and {@code ModTriggers}
 * are not Forge registries: conditions go through
 * {@code CraftingHelper.register} (WR-124), triggers through
 * {@code CriteriaTriggers.register} (achievements task).
 */
public final class ModRegistries {

    /** Every register; the order follows the table of §3. */
    public static final List<DeferredRegister<?>> ALL = List.of(
            ModBlocks.REGISTER, ModItems.REGISTER, ModBlockEntities.REGISTER, ModMenus.REGISTER,
            ModRecipeSerializers.REGISTER, ModLootModifiers.REGISTER, ModPoiTypes.REGISTER,
            ModProfessions.REGISTER, ModFeatures.REGISTER, ModProcessors.REGISTER, ModPoolElements.REGISTER,
            ModCreativeTabs.REGISTER);

    private ModRegistries() {
    }

    /** Attaches every register to the mod event bus. */
    public static void register(IEventBus modBus) {
        for (DeferredRegister<?> r : ALL) {
            r.register(modBus);
        }
    }
}
