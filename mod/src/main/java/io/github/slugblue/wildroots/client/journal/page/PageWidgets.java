package io.github.slugblue.wildroots.client.journal.page;

import io.github.slugblue.wildroots.client.journal.JournalColors;
import io.github.slugblue.wildroots.client.journal.book.JournalAtlas;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import java.util.List;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Clickable parts of pages (journal v2 §2.4, §3.0): a link in text and an
 * item icon. Both take Tab focus and Enter, show a frame on hover and
 * focus, and open their entry with a history step.
 */
@OnlyIn(Dist.CLIENT)
public final class PageWidgets {

    private PageWidgets() {
    }

    static void focusRing(GuiGraphics g, int x, int y, int w, int h) {
        JournalAtlas.nineSlice(g, JournalAtlas.FOCUS, JournalAtlas.FOCUS_BORDER, x, y, w, h);
    }

    /** The hit area of a link run on one line; the text itself is drawn by its page. */
    public static final class LinkWidget extends AbstractButton {

        private final String target;
        private final PageContext ctx;

        public LinkWidget(int x, int y, int w, String target, PageContext ctx) {
            super(x, y, Math.max(1, w), PageRect.LINE_HEIGHT, Component.translatable("wildroots.journal." + target
                    + ".title"));
            this.target = target;
            this.ctx = ctx;
            setTooltip(Tooltip.create(getMessage()));
        }

        public String target() {
            return target;
        }

        @Override
        public void onPress() {
            ctx.open(target);
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            if (isHoveredOrFocused()) {
                g.hLine(getX(), getX() + width - 1, getY() + 8, JournalColors.LINK);
            }
            if (isFocused()) {
                focusRing(g, getX() - 2, getY() - 2, width + 4, height + 3);
            }
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput out) {
            defaultButtonNarrationText(out);
        }
    }

    /**
     * An item drawn on a page at 16 or 32 px: the vanilla tooltip on hover,
     * a click opens the entry that lists the item (П3), if one is open.
     * Several stacks (a tag ingredient) are shown in turn, one per
     * {@link #cycle(int)} step.
     */
    public static final class ItemWidget extends AbstractButton {

        private final List<ItemStack> stacks;
        private final PageContext ctx;
        private final boolean[] linked;
        private int shown;

        public ItemWidget(int x, int y, int size, ItemStack stack, PageContext ctx) {
            this(x, y, size, List.of(stack), ctx);
        }

        public ItemWidget(int x, int y, int size, List<ItemStack> stacks, PageContext ctx) {
            super(x, y, size, size, stacks.isEmpty() ? Component.empty() : stacks.get(0).getHoverName());
            this.stacks = List.copyOf(stacks);
            this.ctx = ctx;
            this.linked = new boolean[stacks.size()];
            for (int i = 0; i < stacks.size(); i++) {
                ItemStack s = stacks.get(i);
                linked[i] = !s.isEmpty() && ctx.hasEntryFor(ForgeRegistries.ITEMS.getKey(s.getItem()));
            }
        }

        public ItemStack stack() {
            return stacks.isEmpty() ? ItemStack.EMPTY : stacks.get(shown);
        }

        /** Shows stack {@code step} modulo the count (a tag ingredient turns once a second, §3.2). */
        public void cycle(int step) {
            if (!stacks.isEmpty()) {
                shown = Math.floorMod(step, stacks.size());
                setMessage(stacks.get(shown).getHoverName());
            }
        }

        @Override
        public void onPress() {
            if (!stacks.isEmpty() && linked[shown]) {
                ctx.openItem(ForgeRegistries.ITEMS.getKey(stack().getItem()));
            }
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            ItemStack stack = stack();
            if (stack.isEmpty()) {
                return;
            }
            g.pose().pushPose();
            g.pose().translate(getX(), getY(), 0);
            float s = width / 16f;
            g.pose().scale(s, s, 1f);
            g.renderItem(stack, 0, 0);
            g.pose().popPose();
            if (isHovered()) {
                ctx.itemTooltip(stack);
            }
            if (linked[shown] && isHoveredOrFocused() || isFocused()) {
                focusRing(g, getX() - 1, getY() - 1, width + 2, height + 2);
            }
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput out) {
            defaultButtonNarrationText(out);
        }
    }
}
