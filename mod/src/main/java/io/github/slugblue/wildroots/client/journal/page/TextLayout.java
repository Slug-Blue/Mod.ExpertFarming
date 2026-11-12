package io.github.slugblue.wildroots.client.journal.page;

import io.github.slugblue.wildroots.client.journal.book.Paginator;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Wraps components into half pages (journal v2 §5.3): {@code Font.split}
 * to {@link PageRect#HALF_WIDTH}, then {@link Paginator} with
 * {@link PageRect#LINES} lines a page. Runs at layout time only.
 */
@OnlyIn(Dist.CLIENT)
public final class TextLayout {

    /** A wrapped line and the journal entry it shows (for MARK_READ), or {@code null}. */
    public record Line(FormattedCharSequence text, String entryId) {

        static final Line BLANK = new Line(FormattedCharSequence.EMPTY, null);
    }

    private TextLayout() {
    }

    public static MutableComponent heading(Component c) {
        return c.copy().withStyle(s -> s.withBold(true));
    }

    /** One block: a heading or a paragraph, wrapped to {@code width}, tagged with an entry id. */
    public static Paginator.Block<Line> block(Font font, FormattedText text, int width, boolean heading,
                                              String entryId) {
        List<Line> lines = new ArrayList<>();
        for (FormattedCharSequence s : font.split(text, width)) {
            lines.add(new Line(s, entryId));
        }
        return new Paginator.Block<>(lines, heading);
    }

    /**
     * Paragraphs of a translated text: the translation is split on
     * {@code \n}, each part one paragraph.
     */
    public static List<Paginator.Block<Line>> paragraphs(Font font, Component text, int width, String entryId) {
        List<Paginator.Block<Line>> out = new ArrayList<>();
        for (String part : text.getString().split("\n")) {
            if (!part.isBlank()) {
                out.add(block(font, Component.literal(part.strip()).withStyle(text.getStyle()), width, false, entryId));
            }
        }
        return out;
    }

    public static List<List<Line>> pages(List<Paginator.Block<Line>> blocks) {
        return Paginator.paginate(blocks, PageRect.LINES, Line.BLANK);
    }

    /** Half pages of text views, one per page of {@code blocks}. */
    public static List<LaidOutPage> textPages(List<Paginator.Block<Line>> blocks) {
        List<LaidOutPage> out = new ArrayList<>();
        for (List<Line> page : pages(blocks)) {
            List<FormattedCharSequence> lines = page.stream().map(Line::text).toList();
            List<String> ids = page.stream().map(Line::entryId).filter(id -> id != null).distinct().toList();
            out.add(new LaidOutPage(PageSpan.HALF, rect -> new TextPageView(rect, lines), ids));
        }
        return out;
    }
}
