package io.github.slugblue.wildroots.client.journal.page;

import io.github.slugblue.wildroots.client.journal.JournalColors;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.FormattedCharSequence;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/** A page of wrapped lines, drawn as they are (the wrapping was done at layout time). */
@OnlyIn(Dist.CLIENT)
public final class TextPageView implements PageView {

    private final PageRect rect;
    private final List<FormattedCharSequence> lines;

    public TextPageView(PageRect rect, List<FormattedCharSequence> lines) {
        this.rect = rect;
        this.lines = List.copyOf(lines);
    }

    @Override
    public PageRect rect() {
        return rect;
    }

    public List<FormattedCharSequence> lines() {
        return lines;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        Font font = Minecraft.getInstance().font;
        int y = rect.y();
        for (FormattedCharSequence line : lines) {
            g.drawString(font, line, rect.x(), y, JournalColors.INK, false);
            y += PageRect.LINE_HEIGHT;
        }
    }
}
