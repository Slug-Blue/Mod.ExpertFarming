package io.github.slugblue.wildroots.client;

import io.github.slugblue.wildroots.WildRoots;
import io.github.slugblue.wildroots.client.journal.JournalColors;
import io.github.slugblue.wildroots.client.lens.LensSheetView;
import io.github.slugblue.wildroots.core.registry.ModItems;
import io.github.slugblue.wildroots.core.safety.EntryPoint;
import io.github.slugblue.wildroots.core.safety.Guarded;
import io.github.slugblue.wildroots.lens.LensPanel;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderTooltipEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The lens panel on the client: a {@link LensPanel} drawn on the journal's
 * paper by {@link LensSheetView}.
 * <ul>
 *   <li>D2 (2026-11-11): while the hand lens is carried on the mouse cursor
 *   in a container screen and the slot under it holds a seed lot, a mother
 *   root or produce. Vanilla draws no tooltip while something is carried,
 *   so the panel is drawn after the screen ({@link ScreenEvent.Render.Post}).
 *   It works in every container screen, the creative inventory included
 *   (its menu hands the carried stack over to the player's inventory menu).</li>
 *   <li>Playtest 2, item 4: the tooltip of an inspected lot, an inspected
 *   mother root or produce. The panel owns the tooltip then: the text lines
 *   are left out (the panel draws the name itself) and the tooltip takes the
 *   paper colours. Holding Shift shows the old text lines instead.</li>
 * </ul>
 */
public final class ClientLensPanel implements ClientTooltipComponent {

    /** Widest panel when the screen allows (a Russian bar label is about 175 px beside its bar). */
    static final int MAX_WIDTH = 240;
    /** Kept free of the screen's width for the tooltip's own offset, padding and the screen edge. */
    static final int SCREEN_MARGIN = 40;

    private final LensPanel data;
    private int width = -1;

    public ClientLensPanel(LensPanel data) {
        this.data = data;
    }

    @Override
    public int getHeight() {
        Font font = Minecraft.getInstance().font;
        return LensSheetView.height(font, data.sheet(), getWidth(font) - 2) + 2;
    }

    @Override
    public int getWidth(Font font) {
        if (width < 0) {
            // real measured text, capped by the screen: wider content wraps inside the card (never past it)
            int screen = Minecraft.getInstance().getWindow().getGuiScaledWidth();
            int cap = Math.max(80, Math.min(MAX_WIDTH, screen - SCREEN_MARGIN));
            width = LensSheetView.width(font, data.sheet(), cap) + 2;
        }
        return width;
    }

    @Override
    @EntryPoint
    public void renderImage(Font font, int x, int y, GuiGraphics g) {
        Guarded.run("lens panel render", "client", () -> {
            // a card of paper under the panel: the tooltip around it keeps vanilla colours when other
            // lines stay (review 1, m6), and the ink reads only on paper
            g.fill(x - 1, y - 1, x + getWidth(font), y + getHeight() - 1, LensSheetView.PAPER);
            LensSheetView.draw(g, font, data.sheet(), x + 1, y, getWidth(font) - 2);
        });
    }

    /** The panel for the screen, or empty: the lens on the cursor over a lot, a mother root or produce. */
    static Optional<LensPanel> panel(AbstractContainerScreen<?> screen) {
        ItemStack carried = screen.getMenu().getCarried();
        if (!carried.is(ModItems.HAND_LENS.get())) {
            return Optional.empty();
        }
        Slot slot = screen.getSlotUnderMouse();
        return slot == null || !slot.hasItem() ? Optional.empty() : cached(slot.getItem());
    }

    /** The last stack, its tag and its panel: one parse while the cursor rests on a slot (review 1, minor 8). */
    private static ItemStack lastStack = ItemStack.EMPTY;
    private static net.minecraft.nbt.CompoundTag lastTag;
    private static boolean lastCreative;
    private static Optional<LensPanel> lastPanel = Optional.empty();

    static Optional<LensPanel> cached(ItemStack stack) {
        boolean creative = Minecraft.getInstance().screen instanceof CreativeModeInventoryScreen;
        if (stack != lastStack || stack.getTag() != lastTag || creative != lastCreative) {
            lastCreative = creative;
            lastStack = stack;
            lastTag = stack.getTag();
            lastPanel = LensPanel.of(stack);
        }
        return lastPanel;
    }

    /** Forge bus, client only. */
    @Mod.EventBusSubscriber(modid = WildRoots.MOD_ID, value = Dist.CLIENT)
    public static final class LensPanelEvents {

        private LensPanelEvents() {
        }

        @SubscribeEvent
        @EntryPoint
        public static void onScreenRender(ScreenEvent.Render.Post event) {
            if (event.getScreen() instanceof AbstractContainerScreen<?> screen) {
                // the line only satisfies the tooltip code; the panel replaces it (onGatherComponents)
                Guarded.run("lens panel", "client", () -> panel(screen).ifPresent(p -> event.getGuiGraphics()
                        .renderTooltip(Minecraft.getInstance().font, List.of(Component.empty()), Optional.of(p),
                                event.getMouseX(), event.getMouseY())));
            }
        }

        @SubscribeEvent
        @EntryPoint
        public static void onGatherComponents(RenderTooltipEvent.GatherComponents event) {
            Guarded.run("lens panel gather", "client", () -> LensPanel.arrange(event.getTooltipElements(),
                    !event.getItemStack().isEmpty(), Screen.hasShiftDown()));
        }

        @SubscribeEvent
        @EntryPoint
        public static void onTooltipColour(RenderTooltipEvent.Color event) {
            Guarded.run("lens panel colour", "client", () -> {
                // the whole tooltip is paper only when the panel is all of it (the cursor panel)
                if (event.getComponents().size() == 1 && event.getComponents().get(0) instanceof ClientLensPanel) {
                    event.setBackground(LensSheetView.PAPER);
                    event.setBorderStart(JournalColors.INK);
                    event.setBorderEnd(JournalColors.INK_FADED);
                }
            });
        }
    }
}
