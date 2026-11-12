package io.github.slugblue.wildroots.client.journal.page;

import io.github.slugblue.wildroots.client.journal.GuideLimits;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Link markup of guide texts (journal v2 §2.4, §5.2): {@code [[id|text]]}
 * or {@code [[id]]}. Parsed once when a page is laid out. A link to an
 * open entry becomes a link; to a closed or unknown id, plain text (no
 * spoiler, no dead end). No nesting and no other commands: anything that
 * is not exactly this shape stays literal text.
 */
@OnlyIn(Dist.CLIENT)
public final class LinkMarkup {

    /**
     * A run of text; {@code target} is the entry id of a working link.
     * {@code titleOf} is set for {@code [[id]]} without text: the link
     * shows the entry's title (the caller translates it).
     */
    public record Segment(String text, Optional<String> target, boolean useTitle) {

        static Segment plain(String s) {
            return new Segment(s, Optional.empty(), false);
        }
    }

    private LinkMarkup() {
    }

    /**
     * @param open whether an id is an open entry (only those become links)
     */
    public static List<Segment> parse(String text, Predicate<String> open) {
        List<Segment> out = new ArrayList<>();
        StringBuilder plain = new StringBuilder();
        int i = 0;
        while (i < text.length()) {
            int start = text.indexOf("[[", i);
            if (start < 0) {
                plain.append(text, i, text.length());
                break;
            }
            int end = text.indexOf("]]", start + 2);
            if (end < 0) {
                plain.append(text, i, text.length());
                break;
            }
            String inner = text.substring(start + 2, end);
            if (inner.contains("[[")) {
                // nesting is not supported: keep the outer "[[" literal and look again after it
                plain.append(text, i, start + 2);
                i = start + 2;
                continue;
            }
            int bar = inner.indexOf('|');
            String id = bar < 0 ? inner : inner.substring(0, bar);
            String shown = bar < 0 ? null : inner.substring(bar + 1);
            if (!GuideLimits.ID.matcher(id).matches() || shown != null && (shown.isEmpty() || shown.contains("|"))) {
                plain.append(text, i, end + 2);
                i = end + 2;
                continue;
            }
            plain.append(text, i, start);
            boolean link = open.test(id);
            if (shown == null) {
                if (!plain.isEmpty()) {
                    out.add(Segment.plain(plain.toString()));
                    plain.setLength(0);
                }
                out.add(new Segment(id, link ? Optional.of(id) : Optional.empty(), true));
            } else if (link) {
                if (!plain.isEmpty()) {
                    out.add(Segment.plain(plain.toString()));
                    plain.setLength(0);
                }
                out.add(new Segment(shown, Optional.of(id), false));
            } else {
                plain.append(shown);
            }
            i = end + 2;
        }
        if (!plain.isEmpty()) {
            out.add(Segment.plain(plain.toString()));
        }
        return out;
    }

    /** The text without markup, as the reader sees it ({@code [[id]]} shows {@code titleOf(id)}). */
    public static String plainText(String text, java.util.function.Function<String, String> titleOf) {
        StringBuilder b = new StringBuilder();
        for (Segment s : parse(text, id -> false)) {
            b.append(s.useTitle() ? titleOf.apply(s.text()) : s.text());
        }
        return b.toString();
    }
}
