package io.github.slugblue.wildroots.core.registry;

import io.github.slugblue.wildroots.core.line.LineLoadedCondition;
import net.minecraftforge.common.crafting.CraftingHelper;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;

/**
 * Recipe and data-pack conditions of the mod (docs/05-architecture.md §3,
 * §9.2): {@code wildroots:line_loaded}. Forge 47 keeps condition
 * serializers outside the registries; they are registered in
 * {@code FMLCommonSetupEvent#enqueueWork} (S1).
 */
public final class ModConditions {

    private ModConditions() {
    }

    public static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> CraftingHelper.register(LineLoadedCondition.SERIALIZER));
    }
}
