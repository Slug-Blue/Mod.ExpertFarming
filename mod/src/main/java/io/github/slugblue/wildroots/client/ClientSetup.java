package io.github.slugblue.wildroots.client;

import io.github.slugblue.wildroots.WildRoots;
import io.github.slugblue.wildroots.core.Ids;
import io.github.slugblue.wildroots.core.registry.ModItems;
import io.github.slugblue.wildroots.item.ProduceStacks;
import io.github.slugblue.wildroots.item.SeedPouchItem;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * Client setup (docs/05-architecture.md §15): item model properties. The
 * seed pouch has {@code wildroots:filled} = 1 when it holds at least one
 * seed (its model override is drawn by the Technical Artist, WR-193/194).
 */
@Mod.EventBusSubscriber(modid = WildRoots.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ClientSetup {

    public static final ResourceLocation FILLED = Ids.mod("filled");
    /** Root colour of the eastern carrot and the carrot, relative to the item's default (0 = the default; WR-706). */
    public static final ResourceLocation ROOT_COLOUR = Ids.mod("root_colour");

    private ClientSetup() {
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            // the creative inventory is client-only, a click cannot inspect there: the panel says so
            io.github.slugblue.wildroots.lens.LensPanel.creativeScreen(() -> net.minecraft.client.Minecraft
                    .getInstance().screen instanceof net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen);
            ItemProperties.register(ModItems.SEED_POUCH.get(), FILLED,
                    (stack, level, entity, seed) -> SeedPouchItem.filled(stack) ? 1f : 0f);
            // the value is the colour relative to the item's default, so each item's model list starts at its own base
            for (var item : java.util.List.of(ModItems.EASTERN_CARROT.get(), ModItems.CARROT.get())) {
                ItemProperties.register(item, ROOT_COLOUR, (stack, level, entity, seed) ->
                        (item.colourOf(stack) - item.defaultColour() + ProduceStacks.COLOURS) % ProduceStacks.COLOURS);
            }
        });
    }
}
