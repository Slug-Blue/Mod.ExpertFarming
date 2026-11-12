package io.github.slugblue.wildroots.client.journal.book;

import io.github.slugblue.wildroots.client.journal.page.PageSpan;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Places laid-out pages on spreads (journal v2 §3.0, §3.13): half pages
 * fill the left side, then the right; a page over the spread always opens
 * a new spread, leaving the right side of the previous one empty.
 */
@OnlyIn(Dist.CLIENT)
public final class Spreads {

    /** A spread: {@code left} and {@code right} halves, or {@code whole}; any may be {@code null}. */
    public record Slots<P>(P left, P right, P whole) {
    }

    private Spreads() {
    }

    public static <P> List<Slots<P>> assemble(List<P> pages, Function<P, PageSpan> span) {
        List<Slots<P>> out = new ArrayList<>();
        P left = null;
        for (P p : pages) {
            if (span.apply(p) == PageSpan.SPREAD) {
                if (left != null) {
                    out.add(new Slots<>(left, null, null));
                    left = null;
                }
                out.add(new Slots<>(null, null, p));
            } else if (left == null) {
                left = p;
            } else {
                out.add(new Slots<>(left, p, null));
                left = null;
            }
        }
        if (left != null) {
            out.add(new Slots<>(left, null, null));
        }
        return out;
    }
}
