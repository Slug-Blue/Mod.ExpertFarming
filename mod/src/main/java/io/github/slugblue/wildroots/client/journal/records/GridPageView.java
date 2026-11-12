package io.github.slugblue.wildroots.client.journal.records;

import com.mojang.blaze3d.systems.RenderSystem;
import io.github.slugblue.wildroots.client.journal.JournalColors;
import io.github.slugblue.wildroots.client.journal.book.BookIcon;
import io.github.slugblue.wildroots.client.journal.book.JournalAtlas;
import io.github.slugblue.wildroots.client.journal.page.LaidOutPage;
import io.github.slugblue.wildroots.client.journal.page.PageRect;
import io.github.slugblue.wildroots.client.journal.page.PageView;
import java.util.ArrayList;
import java.util.List;
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
 * A grid page of records (journal v2 §3.7): a counter line, then cells of
 * an icon and a name, {@value #COLUMNS} × {@value #ROWS}. A closed
 * cultivar is a silhouette (its item in one dark colour) with "???".
 */
@OnlyIn(Dist.CLIENT)
public final class GridPageView implements PageView {

    public static final int COLUMNS = 2;
    public static final int ROWS = 8;
    static final int CELL_H = 17;
    static final int COUNTER_H = 13;

    /** One cell. */
    public record Cell(BookIcon icon, Component name, boolean silhouette, boolean isNew, Runnable action) {
    }

    private final PageRect rect;
    private final Component counter;
    private final List<CellButton> cells = new ArrayList<>();

    GridPageView(PageRect rect, Component counter, List<Cell> page) {
        this.rect = rect;
        this.counter = counter;
        int w = rect.width() / COLUMNS;
        for (int i = 0; i < page.size(); i++) {
            int x = rect.x() + (i % COLUMNS) * w;
            int y = rect.y() + COUNTER_H + (i / COLUMNS) * CELL_H;
            cells.add(new CellButton(x, y, w - 2, page.get(i)));
        }
    }

    /** Pages of {@value #COLUMNS} × {@value #ROWS} cells, each with the counter on top. */
    public static List<LaidOutPage> pages(Component counter, List<Cell> cells) {
        List<LaidOutPage> out = new ArrayList<>();
        int per = COLUMNS * ROWS;
        for (int i = 0; i < Math.max(1, cells.size()); i += per) {
            List<Cell> page = cells.subList(Math.min(i, cells.size()), Math.min(cells.size(), i + per));
            out.add(LaidOutPage.half(rect -> new GridPageView(rect, counter, page)));
        }
        return out;
    }

    @Override
    public PageRect rect() {
        return rect;
    }

    @Override
    public List<CellButton> widgets() {
        return cells;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.drawString(Minecraft.getInstance().font, counter, rect.x(), rect.y(), JournalColors.INK_FADED, false);
    }

    /** A cell: icon and name (cut to fit, the full name as tooltip). */
    static final class CellButton extends AbstractButton {

        private final Cell cell;
        private final Component shown;
        private final int shownWidth;

        CellButton(int x, int y, int w, Cell cell) {
            super(x, y, w, CELL_H - 1, cell.name());
            this.cell = cell;
            Font font = Minecraft.getInstance().font;
            int max = w - 19 - (cell.isNew() ? 8 : 0);
            Component s = cell.name();
            if (font.width(s) > max) {
                s = Component.literal(font.plainSubstrByWidth(s.getString(), Math.max(0, max - font.width("…"))) + "…");
            }
            this.shown = s;
            this.shownWidth = font.width(s);
            setTooltip(Tooltip.create(cell.name()));
        }

        @Override
        public void onPress() {
            cell.action().run();
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            Font font = Minecraft.getInstance().font;
            if (cell.silhouette()) {
                RenderSystem.setShaderColor(0.22f, 0.18f, 0.14f, 1f);
                cell.icon().draw(g, getX(), getY(), 16);
                g.flush();
                RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
            } else {
                cell.icon().draw(g, getX(), getY(), 16);
            }
            int color = isHoveredOrFocused() ? JournalColors.LINK : JournalColors.INK;
            g.drawString(font, shown, getX() + 19, getY() + 4, color, false);
            if (cell.isNew()) {
                JournalAtlas.blit(g, JournalAtlas.NEW_DOT, getX() + 19 + shownWidth + 1, getY() + 4);
            }
            if (isHoveredOrFocused()) {
                JournalAtlas.nineSlice(g, JournalAtlas.FOCUS, JournalAtlas.FOCUS_BORDER, getX() - 1, getY() - 1,
                        width + 2, height + 2);
            }
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput out) {
            defaultButtonNarrationText(out);
        }
    }
}
