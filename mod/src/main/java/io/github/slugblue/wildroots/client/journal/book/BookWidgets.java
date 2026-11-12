package io.github.slugblue.wildroots.client.journal.book;

import io.github.slugblue.wildroots.client.journal.JournalColors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * The book's clickable parts (journal v2 §3.0): everything hoverable has a
 * tooltip, everything clickable gets a frame on hover and keyboard focus,
 * Tab moves the focus and Enter presses (vanilla widget behaviour).
 */
@OnlyIn(Dist.CLIENT)
public final class BookWidgets {

    private BookWidgets() {
    }

    static void focusFrame(GuiGraphics g, int x, int y, int w, int h) {
        g.renderOutline(x, y, w, h, JournalColors.FOCUS);
    }

    /** A 16 × 16 sprite button of the atlas. */
    public static final class SpriteButton extends AbstractButton {

        private final JournalAtlas.Button sprite;
        private final Runnable action;

        public SpriteButton(int x, int y, JournalAtlas.Button sprite, Component tooltip, boolean enabled,
                            Runnable action) {
            super(x, y, BookGeometry.BUTTON, BookGeometry.BUTTON, tooltip);
            this.sprite = sprite;
            this.action = action;
            this.active = enabled;
            setTooltip(Tooltip.create(tooltip));
        }

        public JournalAtlas.Button sprite() {
            return sprite;
        }

        @Override
        public void onPress() {
            action.run();
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            JournalAtlas.State state = !active ? JournalAtlas.State.DISABLED
                    : isHoveredOrFocused() ? JournalAtlas.State.HOVERED : JournalAtlas.State.NORMAL;
            JournalAtlas.blit(g, sprite.sprite(state), getX(), getY());
            if (active && isFocused()) {
                focusFrame(g, getX(), getY(), width, height);
            }
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput out) {
            defaultButtonNarrationText(out);
        }
    }

    /** A category tab on the left edge: 16 × 20 visible, a 12 px icon, a dot when it holds something new. */
    public static final class TabButton extends AbstractButton {

        static final int VISIBLE_W = 16;
        private final BookIcon icon;
        private final boolean selected;
        private final boolean isNew;
        private final Runnable action;

        public TabButton(int x, int y, BookIcon icon, Component name, boolean selected, boolean isNew, Runnable action) {
            super(x, y, VISIBLE_W, BookGeometry.TAB_H, name);
            this.icon = icon;
            this.selected = selected;
            this.isNew = isNew;
            this.action = action;
            setTooltip(Tooltip.create(name));
        }

        @Override
        public void onPress() {
            action.run();
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            JournalAtlas.TabState s = selected ? JournalAtlas.TabState.SELECTED
                    : isHoveredOrFocused() ? JournalAtlas.TabState.HOVERED : JournalAtlas.TabState.NORMAL;
            JournalAtlas.tab(g, s, getX(), getY(), VISIBLE_W);
            icon.draw(g, getX() + 2, getY() + 4, 12);
            if (isNew) {
                JournalAtlas.blit(g, JournalAtlas.NEW_DOT, getX() + 1, getY() + 1);
            }
            if (isFocused()) {
                focusFrame(g, getX(), getY(), width, height);
            }
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput out) {
            defaultButtonNarrationText(out);
        }
    }

    /** A row of a list page: icon, label, a count on the right, a "new" dot. */
    public static final class RowButton extends AbstractButton {

        public static final int HEIGHT = 13;
        private final BookIcon icon;
        private final Component right;
        private final boolean isNew;
        private final boolean bullet;
        private final Runnable action;
        /** Measured once (§5.3): the label cut to fit, its width, the count's width. */
        private final Component shownLabel;
        private final int labelWidth;
        private final int rightWidth;
        private final int textX;

        public RowButton(int x, int y, int width, BookIcon icon, Component label, Component right, boolean isNew,
                         boolean bullet, Runnable action) {
            super(x, y, width, HEIGHT, label);
            this.icon = icon;
            this.right = right;
            this.isNew = isNew;
            this.bullet = bullet;
            this.action = action;
            Font font = Minecraft.getInstance().font;
            int tx = 1;
            if (!icon.isEmpty()) {
                tx = 15;
            } else if (bullet) {
                tx = 8;
            }
            this.textX = tx;
            this.rightWidth = right.getString().isEmpty() ? 0 : font.width(right);
            int maxLabel = width - tx - (rightWidth > 0 ? rightWidth + 4 : 0) - (isNew ? 6 : 0);
            Component l = label;
            if (font.width(l) > maxLabel) {
                String cut = font.plainSubstrByWidth(l.getString(), Math.max(0, maxLabel - font.width("…")));
                l = Component.literal(cut + "…").withStyle(label.getStyle());
                setTooltip(Tooltip.create(label));
            }
            this.shownLabel = l;
            this.labelWidth = font.width(l);
        }

        @Override
        public void onPress() {
            action.run();
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            Font font = Minecraft.getInstance().font;
            int tx = getX() + textX;
            if (!icon.isEmpty()) {
                icon.draw(g, getX(), getY(), 12);
            } else if (bullet) {
                g.drawString(font, "•", getX() + 1, getY() + 3, JournalColors.INK, false);
            }
            int color = isHoveredOrFocused() ? JournalColors.LINK : JournalColors.INK;
            g.drawString(font, shownLabel, tx, getY() + 3, color, false);
            if (isNew) {
                JournalAtlas.blit(g, JournalAtlas.NEW_DOT, tx + labelWidth + 2, getY() + 5);
            }
            if (rightWidth > 0) {
                g.drawString(font, right, getX() + width - rightWidth - 1, getY() + 3, JournalColors.INK_FADED, false);
            }
            if (isHoveredOrFocused()) {
                g.hLine(tx, tx + labelWidth - 1, getY() + 12, JournalColors.LINK);
            }
            if (isFocused()) {
                focusFrame(g, getX() - 1, getY(), width + 2, height);
            }
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput out) {
            defaultButtonNarrationText(out);
        }
    }
}
