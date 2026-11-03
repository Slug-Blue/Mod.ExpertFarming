package io.github.slugblue.wildroots.client;

import io.github.slugblue.wildroots.WildRoots;
import io.github.slugblue.wildroots.core.safety.EntryPoint;
import io.github.slugblue.wildroots.core.safety.Guarded;
import io.github.slugblue.wildroots.item.PouchTooltip;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientTooltipComponentFactoriesEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Client tooltip of the seed pouch (docs/05-architecture.md §6.3): the lots
 * inside as item icons, 4 per row, last put in first; it only draws what the
 * server stored, the lot tooltips themselves come from their {@code disp}.
 */
public final class ClientPouchTooltip implements ClientTooltipComponent {

    static final int COLUMNS = 4;
    static final int CELL = 18;

    private final PouchTooltip data;

    ClientPouchTooltip(PouchTooltip data) {
        this.data = data;
    }

    private int rows() {
        return (data.items().size() + COLUMNS - 1) / COLUMNS;
    }

    @Override
    public int getHeight() {
        return rows() * CELL + 4;
    }

    @Override
    public int getWidth(Font font) {
        return Math.min(COLUMNS, data.items().size()) * CELL + 2;
    }

    @Override
    @EntryPoint
    public void renderImage(Font font, int x, int y, GuiGraphics g) {
        Guarded.run("pouch tooltip render", "client", () -> render(font, x, y, g));
    }

    private void render(Font font, int x, int y, GuiGraphics g) {
        for (int i = 0; i < data.items().size(); i++) {
            ItemStack s = data.items().get(i);
            int cx = x + 1 + (i % COLUMNS) * CELL;
            int cy = y + 1 + (i / COLUMNS) * CELL;
            g.renderItem(s, cx, cy, i);
            g.renderItemDecorations(font, s, cx, cy);
        }
    }

    /** Registers the component factory (mod bus, client only). */
    @Mod.EventBusSubscriber(modid = WildRoots.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Registration {

        private Registration() {
        }

        @SubscribeEvent
        @EntryPoint
        public static void onRegister(RegisterClientTooltipComponentFactoriesEvent event) {
            Guarded.run("pouch tooltip factory", "client", () ->
                    event.register(PouchTooltip.class, ClientPouchTooltip::new));
        }
    }
}
