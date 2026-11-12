package io.github.slugblue.wildroots.client.journal.page;

import io.github.slugblue.wildroots.client.journal.JournalColors;
import io.github.slugblue.wildroots.client.journal.book.Paginator;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Wraps components into half pages (journal v2 §5.3): {@code Font.split}
 * to {@link PageRect#HALF_WIDTH}, then {@link Paginator} with
 * {@link PageRect#LINES} lines a page. Guide text may carry link markup
 * ({@link LinkMarkup}); links keep their place on each wrapped line so the
 * page can make them clickable. Runs at layout time only.
 */
@OnlyIn(Dist.CLIENT)
public final class TextLayout {

    /** Marks a link run in a style ({@code Style.insertion}, unused elsewhere in the book). */
    static final String LINK = "wildroots_journal_link:";

    /** A link on one wrapped line: {@code [x0, x1)} px from the line start. */
    public record LinkSpan(int x0, int x1, String target) {
    }

    /** A wrapped line, the journal entry it shows (for MARK_READ) or {@code null}, and its links. */
    public record Line(FormattedCharSequence text, String entryId, List<LinkSpan> links) {

        public Line {
            links = List.copyOf(links);
        }

        static final Line BLANK = new Line(FormattedCharSequence.EMPTY, null, List.of());
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
            lines.add(new Line(s, entryId, spans(font, s)));
        }
        return new Paginator.Block<>(lines, heading);
    }

    /** Link runs of a wrapped line, measured once. */
    static List<LinkSpan> spans(Font font, FormattedCharSequence line) {
        List<LinkSpan> out = new ArrayList<>();
        float[] x = {0};
        String[] cur = {null};
        float[] start = {0};
        line.accept((index, style, cp) -> {
            String ins = style.getInsertion();
            String target = ins != null && ins.startsWith(LINK) ? ins.substring(LINK.length()) : null;
            if (!java.util.Objects.equals(target, cur[0])) {
                if (cur[0] != null) {
                    out.add(new LinkSpan(Math.round(start[0]), Math.round(x[0]), cur[0]));
                }
                cur[0] = target;
                start[0] = x[0];
            }
            x[0] += font.getSplitter().stringWidth(FormattedCharSequence.codepoint(cp, style));
            return true;
        });
        if (cur[0] != null) {
            out.add(new LinkSpan(Math.round(start[0]), Math.round(x[0]), cur[0]));
        }
        return out;
    }

    /**
     * Paragraphs of a translated text: the translation is split on
     * {@code \n}, each part one paragraph. No link markup (server keys).
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

    /**
     * Paragraphs of a guide key, with {@code [[id|text]]} links: open ids
     * become links, others plain text (§2.4).
     */
    public static List<Paginator.Block<Line>> guideParagraphs(Font font, String key, int width, Predicate<String> open) {
        List<Paginator.Block<Line>> out = new ArrayList<>();
        for (String part : Component.translatable(key).getString().split("\n")) {
            if (!part.isBlank()) {
                out.add(block(font, rich(part.strip(), open), width, false, null));
            }
        }
        return out;
    }

    /** One line of guide text as a component: links coloured and tagged. */
    public static MutableComponent rich(String text, Predicate<String> open) {
        MutableComponent c = Component.empty();
        for (LinkMarkup.Segment s : LinkMarkup.parse(text, open)) {
            MutableComponent part = s.useTitle()
                    ? Component.translatable("wildroots.journal." + s.text() + ".title")
                    : Component.literal(s.text());
            if (s.target().isPresent()) {
                String t = s.target().get();
                part.withStyle(Style.EMPTY.withColor(JournalColors.LINK & 0xFFFFFF).withInsertion(LINK + t));
            }
            c.append(part);
        }
        return c;
    }

    public static List<List<Line>> pages(List<Paginator.Block<Line>> blocks) {
        return Paginator.paginate(blocks, PageRect.LINES, Line.BLANK);
    }

    /** Half pages of text views, one per page of {@code blocks}; links open entries through {@code ctx}. */
    public static List<LaidOutPage> textPages(List<Paginator.Block<Line>> blocks, PageContext ctx) {
        List<LaidOutPage> out = new ArrayList<>();
        for (List<Line> page : pages(blocks)) {
            List<String> ids = page.stream().map(Line::entryId).filter(id -> id != null).distinct().toList();
            out.add(new LaidOutPage(PageSpan.HALF, rect -> new TextPageView(rect, page, ctx), ids));
        }
        return out;
    }
}
