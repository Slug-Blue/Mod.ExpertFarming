package io.github.slugblue.wildroots.client.journal.book;

import io.github.slugblue.wildroots.client.journal.JournalColors;
import io.github.slugblue.wildroots.client.journal.page.LaidOutPage;
import io.github.slugblue.wildroots.client.journal.page.PageRect;
import io.github.slugblue.wildroots.client.journal.page.PageView;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * A half page of headings and clickable rows: the contents (§3.8) and the
 * list of a guide category. Rows are {@link BookWidgets.RowButton}s, so
 * they take Tab focus and Enter.
 */
@OnlyIn(Dist.CLIENT)
public final class ListPageView implements PageView {

    /** One line of a list page. */
    public sealed interface Item permits Heading, Gap, Row, Note {

        int height();
    }

    public record Heading(Component text) implements Item {

        @Override
        public int height() {
            return 12;
        }
    }

    /** Wrapped text (a message such as "nothing found"): its lines are wrapped to the page when it is made. */
    public record Note(List<net.minecraft.util.FormattedCharSequence> lines) implements Item {

        public static Note of(Component text) {
            return new Note(List.copyOf(Minecraft.getInstance().font.split(text, PageRect.HALF_WIDTH)));
        }

        @Override
        public int height() {
            return lines.size() * PageRect.LINE_HEIGHT + 4;
        }
    }

    public record Gap() implements Item {

        @Override
        public int height() {
            return 5;
        }
    }

    public record Row(BookIcon icon, Component label, Component right, boolean isNew, boolean bullet,
                      Runnable action) implements Item {

        @Override
        public int height() {
            return BookWidgets.RowButton.HEIGHT;
        }
    }

    private final PageRect rect;
    private final List<Item> items;
    private final List<BookWidgets.RowButton> rows = new ArrayList<>();
    /** Headings cut with an ellipsis to the page width, measured once (a heading is one line). */
    private final java.util.Map<Heading, net.minecraft.util.FormattedCharSequence> headings =
            new java.util.IdentityHashMap<>();

    public ListPageView(PageRect rect, List<Item> items) {
        this.rect = rect;
        this.items = List.copyOf(items);
        var font = Minecraft.getInstance().font;
        int y = rect.y();
        for (Item it : items) {
            if (it instanceof Heading h) {
                net.minecraft.network.chat.FormattedText t = font.width(h.text()) <= rect.width() ? h.text()
                        : net.minecraft.network.chat.FormattedText.composite(font.substrByWidth(h.text(),
                                rect.width() - font.width("…")), net.minecraft.network.chat.FormattedText.of("…"));
                headings.put(h, net.minecraft.locale.Language.getInstance().getVisualOrder(t));
            }
            if (it instanceof Row r) {
                rows.add(new BookWidgets.RowButton(rect.x(), y, rect.width(), r.icon(), r.label(), r.right(), r.isNew(),
                        r.bullet(), r.action()));
            }
            y += it.height();
        }
    }

    /** Splits items into half pages that fit {@link PageRect#HEIGHT}; a heading never ends a page, a gap never starts one. */
    public static List<LaidOutPage> pages(List<Item> items) {
        List<List<Item>> pages = new ArrayList<>();
        List<Item> page = new ArrayList<>();
        int used = 0;
        for (int i = 0; i < items.size(); i++) {
            Item it = items.get(i);
            if (it instanceof Gap && page.isEmpty()) {
                continue;
            }
            int need = it.height() + (it instanceof Heading && i + 1 < items.size() ? items.get(i + 1).height() : 0);
            if (used + need > PageRect.HEIGHT && !page.isEmpty()) {
                pages.add(page);
                page = new ArrayList<>();
                used = 0;
                if (it instanceof Gap) {
                    continue;
                }
            }
            page.add(it);
            used += it.height();
        }
        if (!page.isEmpty() || pages.isEmpty()) {
            pages.add(page);
        }
        List<LaidOutPage> out = new ArrayList<>();
        for (List<Item> p : pages) {
            out.add(LaidOutPage.half(rect -> new ListPageView(rect, p)));
        }
        return out;
    }

    @Override
    public PageRect rect() {
        return rect;
    }

    @Override
    public List<BookWidgets.RowButton> widgets() {
        return rows;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int y = rect.y();
        for (Item it : items) {
            if (it instanceof Heading h) {
                g.drawString(Minecraft.getInstance().font, headings.get(h), rect.x(), y + 1, JournalColors.INK, false);
            } else if (it instanceof Note n) {
                int ly = y;
                for (var line : n.lines()) {
                    g.drawString(Minecraft.getInstance().font, line, rect.x(), ly, JournalColors.INK_FADED, false);
                    ly += PageRect.LINE_HEIGHT;
                }
            }
            y += it.height();
        }
    }
}
