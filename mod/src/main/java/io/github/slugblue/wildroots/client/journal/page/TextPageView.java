package io.github.slugblue.wildroots.client.journal.page;

import io.github.slugblue.wildroots.client.journal.JournalColors;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.FormattedCharSequence;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * A page of wrapped lines, drawn as they are (the wrapping was done at
 * layout time); each link run is a {@link PageWidgets.LinkWidget}.
 */
@OnlyIn(Dist.CLIENT)
public final class TextPageView implements PageView {

    private final PageRect rect;
    private final List<FormattedCharSequence> lines = new ArrayList<>();
    private final List<PageWidgets.LinkWidget> links = new ArrayList<>();

    public TextPageView(PageRect rect, List<TextLayout.Line> page, PageContext ctx) {
        this(rect, page, ctx, rect.y());
    }

    /** Lines from {@code top} down (a page that draws something above its text). */
    public TextPageView(PageRect rect, List<TextLayout.Line> page, PageContext ctx, int top) {
        this.rect = rect;
        int y = top;
        for (TextLayout.Line l : page) {
            lines.add(l.text());
            for (TextLayout.LinkSpan s : l.links()) {
                links.add(new PageWidgets.LinkWidget(rect.x() + s.x0(), y, s.x1() - s.x0(), s.target(), ctx));
            }
            y += PageRect.LINE_HEIGHT;
        }
        this.top = top;
    }

    private final int top;

    @Override
    public PageRect rect() {
        return rect;
    }

    public List<FormattedCharSequence> lines() {
        return lines;
    }

    @Override
    public List<PageWidgets.LinkWidget> widgets() {
        return links;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        Font font = Minecraft.getInstance().font;
        int y = top;
        for (FormattedCharSequence line : lines) {
            g.drawString(font, line, rect.x(), y, JournalColors.INK, false);
            y += PageRect.LINE_HEIGHT;
        }
    }
}
