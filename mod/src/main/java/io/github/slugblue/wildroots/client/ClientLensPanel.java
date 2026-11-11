package io.github.slugblue.wildroots.client;

import io.github.slugblue.wildroots.WildRoots;
import io.github.slugblue.wildroots.client.ui.TraitBar;
import io.github.slugblue.wildroots.core.registry.ModItems;
import io.github.slugblue.wildroots.core.safety.EntryPoint;
import io.github.slugblue.wildroots.core.safety.Guarded;
import io.github.slugblue.wildroots.lens.LensPanel;
import io.github.slugblue.wildroots.lens.LensReading;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientTooltipComponentFactoriesEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The lens panel on the client (D2, 2026-11-11): bars of the tier-1 axes,
 * drawn only while the hand lens is carried on the mouse cursor in a
 * container screen and the slot under it holds a seed lot, a mother root or
 * produce. Vanilla draws no tooltip while something is carried, so the
 * panel is drawn after the screen ({@link ScreenEvent.Render.Post}). It only
 * draws what {@link LensPanel#of} read from the stack.
 */
public final class ClientLensPanel implements ClientTooltipComponent {

    private final LensPanel data;

    ClientLensPanel(LensPanel data) {
        this.data = data;
    }

    @Override
    public int getHeight() {
        return data.bars().size() * TraitBar.ROW_HEIGHT + (data.bars().isEmpty() ? 0 : 2);
    }

    @Override
    public int getWidth(Font font) {
        int w = 0;
        for (LensPanel.Bar b : data.bars()) {
            w = Math.max(w, TraitBar.width(font, b.max(), label(b)) + 2);
        }
        return w;
    }

    static Component label(LensPanel.Bar b) {
        return Component.translatable(LensReading.TRAIT, b.label(), b.word());
    }

    @Override
    @EntryPoint
    public void renderImage(Font font, int x, int y, GuiGraphics g) {
        Guarded.run("lens panel render", "client", () -> {
            for (int i = 0; i < data.bars().size(); i++) {
                LensPanel.Bar b = data.bars().get(i);
                TraitBar.draw(g, x + 1, y + 1 + i * TraitBar.ROW_HEIGHT, b.step(), b.max(), label(b));
            }
        });
    }

    /** The panel for the screen, or empty: the lens on the cursor over a lot, a mother root or produce. */
    static Optional<LensPanel> panel(AbstractContainerScreen<?> screen) {
        ItemStack carried = screen.getMenu().getCarried();
        if (!carried.is(ModItems.HAND_LENS.get())) {
            return Optional.empty();
        }
        Slot slot = screen.getSlotUnderMouse();
        return slot == null || !slot.hasItem() ? Optional.empty() : LensPanel.of(slot.getItem());
    }

    /** Draws the panel after the screen (Forge bus, client only). */
    @Mod.EventBusSubscriber(modid = WildRoots.MOD_ID, value = Dist.CLIENT)
    public static final class Events {

        private Events() {
        }

        @SubscribeEvent
        @EntryPoint
        public static void onScreenRender(ScreenEvent.Render.Post event) {
            if (event.getScreen() instanceof AbstractContainerScreen<?> screen) {
                Guarded.run("lens panel", "client", () -> panel(screen).ifPresent(p -> event.getGuiGraphics()
                        .renderTooltip(Minecraft.getInstance().font, p.lines(), Optional.of(p), event.getMouseX(),
                                event.getMouseY())));
            }
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
            Guarded.run("lens panel factory", "client", () ->
                    event.register(LensPanel.class, ClientLensPanel::new));
        }
    }
}
